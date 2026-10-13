import com.edgeore.app.io.BoundedInput
import com.edgeore.app.receipts.ReceiptVerifier
import java.io.File
import java.io.PrintStream
import kotlin.system.exitProcess

/** Export fields outside every receipt-body and checkpoint signature; only the optional envelope covers them (docs/hardening-backlog.md H4). */
const val UNSIGNED_EXPORT_FIELDS = "top-level note, exclusions, payment, location, exportedAt; deviceKeys[].protection, deviceKeys[].firstUsedAt"

/** Exit 0: integrity accepted; 1: rejected; 2: invocation/read/runtime error. */
fun checkReceipt(
    args: Array<String>,
    out: PrintStream = System.out,
    err: PrintStream = System.err,
): Int {
    if (args.size == 2 && args[0] == "--round-proof") return checkRoundProof(args[1], out, err)
    if (args.size == 1 && args[0].endsWith(".oreproof")) return checkOreProof(args[0], out, err)
    if (args.size !in setOf(1, 3) || (args.size == 3 && args[1] != "--trusted-key")) {
        err.println("Usage: verify-receipt.sh EXPORT.json [--trusted-key DEVICE-SPKI.der] | --round-proof BUNDLE.json")
        return 2
    }
    return try {
        val input =
            File(args[0]).inputStream().use {
                BoundedInput.readUtf8Text(it, 64L * 1024 * 1024, 64 * 1024 * 1024)
            }
        val text =
            when (input) {
                is BoundedInput.TextResult.Ok -> input.text
                is BoundedInput.TextResult.Refused -> {
                    out.println("VERIFIED: FAIL: ${input.reason}")
                    return 1
                }
            }
        val key = if (args.size == 3) File(args[2]).inputStream().use { BoundedInput.readAtMost(it, 16 * 1024) } else null
        val report = ReceiptVerifier.verify(text, trustedDeviceKeySpki = key)
        out.println(if (report.accepted) "VERIFIED: PASS" else "VERIFIED: FAIL")
        out.println(report.summary)
        report.findings.forEach { out.println("Finding: $it") }
        // Receipt schema v3: the device signature also names the descriptive fields; v1/v2 sign the body digest only.
        out.println(
            "Receipt signatures: v3 (descriptive fields bound in the signature) ${report.v3Receipts}, legacy digest-only (v1/v2) ${report.legacyReceipts}",
        )
        // Hardening backlog H4: exports since integration 0.2.9 sign these fields in an "envelope"; older (or stripped) ones do not.
        // Exports that declare envelopeRequired=true are rejected above when the envelope is missing.
        when {
            report.descriptiveFieldsSigned ->
                out.println(
                    "Descriptive export fields covered by the signed envelope: $UNSIGNED_EXPORT_FIELDS",
                )
            report.legacyWithoutEnvelope ->
                out.println(
                    "Not covered by any signature (legacy export without envelope, descriptive only): $UNSIGNED_EXPORT_FIELDS",
                )
            else -> out.println("Not covered by any signature (descriptive only, no signed envelope): $UNSIGNED_EXPORT_FIELDS")
        }
        if (report.accepted) 0 else 1
    } catch (e: java.io.IOException) {
        err.println("CHECKER ERROR: cannot read input (${e.javaClass.simpleName})")
        2
    } catch (e: Exception) {
        // Malformed exports can throw inside the existing verifier; fail closed.
        out.println("VERIFIED: FAIL: verifier refused input (${e.javaClass.simpleName})")
        1
    }
}

/**
 * Round Proof bundle: first the embedded receipt export must pass ReceiptVerifier (this verifies the integrity of signed
 * receipt contents); then the proof's SHA-256 must be one of the verified receipts' digests.roundProofSha256; then every
 * derived result is recomputed from the raw account bytes. Exit 0 accepted, 1 rejected, 2 read error.
 */
fun checkRoundProof(
    path: String,
    out: PrintStream,
    err: PrintStream,
): Int {
    val text =
        try {
            File(path).inputStream().use { BoundedInput.readUtf8Text(it, 64L * 1024 * 1024, 64 * 1024 * 1024) }
        } catch (e: java.io.IOException) {
            err.println("CHECKER ERROR: cannot read input (${e.javaClass.simpleName})")
            return 2
        }
    val body =
        when (text) {
            is BoundedInput.TextResult.Ok -> text.text
            is BoundedInput.TextResult.Refused -> {
                out.println("ROUND PROOF: FAIL: ${text.reason}")
                return 1
            }
        }
    return try {
        val doc = org.json.JSONObject(body)
        if (doc.optString("schema") != com.edgeore.app.edge.RoundProof.BUNDLE_SCHEMA) {
            out.println("ROUND PROOF: FAIL: not a ${com.edgeore.app.edge.RoundProof.BUNDLE_SCHEMA} bundle")
            return 1
        }
        val export = doc.getString("receiptExport")
        val report = ReceiptVerifier.verify(export)
        out.println("Receipts: " + report.summary)
        if (!report.accepted) {
            out.println("ROUND PROOF: FAIL: receipt export rejected")
            return 1
        }
        val digests =
            org.json.JSONObject(export).getJSONArray("receipts").let { a ->
                (0 until a.length()).mapNotNull { i ->
                    org.json.JSONObject(a.getJSONObject(i).getString("body")).optJSONObject("digests")?.optString(
                        com.edgeore.app.edge.RoundProof.DIGEST_KEY,
                    )?.takeIf { it.isNotEmpty() }
                }
            }
        val c = com.edgeore.app.edge.RoundProof.check(doc.getString("proofJson"), digests, com.edgeore.app.ore.OreV3::roundAddress)
        out.println(if (c.accepted) "ROUND PROOF: PASS" else "ROUND PROOF: FAIL")
        c.winningSquare?.let { out.println("Winning square (re-derived): $it") }
        c.verified.forEach { out.println("Verified: $it") }
        c.failures.forEach { out.println("Failed: $it") }
        c.unverified.forEach { out.println("Not verified: $it") }
        if (c.accepted) 0 else 1
    } catch (e: Exception) {
        out.println("ROUND PROOF: FAIL: refused input (${e.javaClass.simpleName})")
        1
    }
}

/**
 * `.oreproof` (edgeore.roundproof/v0): verifies the integrity of signed receipt contents in receipt_export, then reports
 * P1 intent, P2 winner, P3 split/solo, P4 SOL accounting, P5 ORE share as PASS / FAIL / UNVERIFIED. The optional
 * "attestation" section is ignored. Exit 0 when no claim FAILs and P1 and P2 PASS; 1 otherwise; 2 read error.
 */
fun checkOreProof(
    path: String,
    out: PrintStream,
    err: PrintStream,
): Int {
    val text =
        try {
            when (val t = File(path).inputStream().use { BoundedInput.readUtf8Text(it, 64L * 1024 * 1024, 64 * 1024 * 1024) }) {
                is BoundedInput.TextResult.Ok -> t.text
                is BoundedInput.TextResult.Refused -> {
                    out.println("OREPROOF: FAIL: ${t.reason}")
                    return 1
                }
            }
        } catch (e: java.io.IOException) {
            err.println("CHECKER ERROR: cannot read input (${e.javaClass.simpleName})")
            return 2
        }
    return try {
        val export = org.json.JSONObject(text).getString("receipt_export")
        val report = ReceiptVerifier.verify(export)
        out.println("Receipts: " + report.summary)
        val bodies =
            if (!report.accepted) {
                emptyMap()
            } else {
                org.json.JSONObject(export).getJSONArray("receipts").let { a ->
                    (0 until a.length()).associate { i -> org.json.JSONObject(a.getJSONObject(i).getString("body")).let { it.getString("id") to it } }
                }
            }
        val claims = com.edgeore.app.edge.OreProofV0.check(text, report.accepted, report.walletSignaturesVerified, bodies, com.edgeore.app.ore.OreV3::roundAddress)
        claims.forEach { out.println("${it.id} ${it.name}: ${it.verdict} (${it.why})") }
        out.println("Not verified by any claim: that the RPC returned what the chain held; how slot_hash was produced; any fairness property beyond these rules.")
        val v = claims.associate { it.id to it.verdict }
        val ok = claims.none { it.verdict == com.edgeore.app.edge.OreProofV0.Verdict.FAIL } &&
            v["P1"] == com.edgeore.app.edge.OreProofV0.Verdict.PASS && v["P2"] == com.edgeore.app.edge.OreProofV0.Verdict.PASS
        out.println(if (ok) "OREPROOF: PASS" else "OREPROOF: FAIL")
        if (ok) 0 else 1
    } catch (e: Exception) {
        out.println("OREPROOF: FAIL: refused input (${e.javaClass.simpleName})")
        1
    }
}

fun main(args: Array<String>) {
    exitProcess(checkReceipt(args))
}
