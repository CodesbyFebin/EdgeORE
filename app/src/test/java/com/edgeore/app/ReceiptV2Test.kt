package com.edgeore.app

import com.edgeore.app.crypto.Sha256
import com.edgeore.app.receipts.Evidence
import com.edgeore.app.receipts.KeyRegistry
import com.edgeore.app.receipts.ReceiptDraft
import com.edgeore.app.receipts.ReceiptKind
import com.edgeore.app.receipts.ReceiptLog
import com.edgeore.app.receipts.ReceiptVerifier
import com.edgeore.app.receipts.SoftwareReceiptSigner
import com.edgeore.app.receipts.WalletEvidence
import com.edgeore.app.receipts.bundleDigest
import com.edgeore.app.receipts.keyId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64

/** P1: wallet-aware verifier, key epochs, corruption-aware reads, explicit completeness and provenance. */
class ReceiptV2Test {
    @get:Rule val tmp = TemporaryFolder()
    private val fx = SignedFixture()
    private val logFile get() = File(tmp.root, "receipts/receipts.jsonl")
    private val registry get() = KeyRegistry(File(tmp.root, "receipts/keys.json"))

    private fun walletDraft(f: SignedFixture = fx, outcome: String = "SIGNED_NOT_BROADCAST", payment: String = "NOT_OBSERVED") =
        ReceiptDraft(ReceiptKind.REVIEW, outcome, "Signed", "test", "MWA", "devnet", operationId = "op-1", solanaSignature = f.signature,
            digests = mapOf("messageSha256" to f.messageSha256), lamports = f.lamports, payment = payment,
            wallet = WalletEvidence(Base64.getEncoder().encodeToString(f.message), f.signer, f.signature))

    private fun resign(doc: JSONObject, index: Int, signer: SoftwareReceiptSigner, edit: (JSONObject) -> Unit) {
        val arr = doc.getJSONArray("receipts")
        val e = arr.getJSONObject(index)
        val body = JSONObject(e.getString("body")).also(edit).toString()
        val sha = Sha256.hex(body)
        e.put("body", body).put("sha256", sha).put("deviceSignature", Base64.getEncoder().encodeToString(signer.sign(sha.toByteArray())))
        doc.put("bundleSha256", bundleDigest((0 until arr.length()).map { arr.getJSONObject(it).getString("sha256") }))
    }

    @Test fun walletSignatureVerifiesIndependentlyOverTheReviewedMessage() {
        val s = SoftwareReceiptSigner()
        val log = ReceiptLog(logFile, s, keys = registry)
        log.append(walletDraft())
        val r = ReceiptVerifier.verify(log.export(), s.publicKeySpki(), emptyList())
        assertTrue(r.findings.toString(), r.accepted)
        assertEquals(1, r.walletSignaturesVerified)
        assertEquals(ReceiptVerifier.Provenance.PINNED_KEY, r.provenance)
        assertEquals(ReceiptVerifier.Completeness.FULL_CHAIN_CHECKPOINTED, r.completeness)
        assertTrue(r.summary.contains("chain status not checked offline"))
    }

    @Test fun appSignedButWalletForgedIsRejected() {
        val s = SoftwareReceiptSigner()
        val log = ReceiptLog(logFile, s, keys = registry)
        log.append(walletDraft())
        // The exporting app itself re-signs a body whose amount was changed: app integrity holds, wallet evidence does not.
        val doc = JSONObject(log.export(listOf(log.all().single())))
        val other = SignedFixture(lamports = 9_000_000)
        resign(doc, 0, s) { b -> b.getJSONObject("wallet").put("messageBase64", Base64.getEncoder().encodeToString(other.message)); b.getJSONObject("digests").put("messageSha256", other.messageSha256) }
        val r = ReceiptVerifier.verify(doc.toString(), s.publicKeySpki(), emptyList())
        assertFalse(r.accepted)
        assertTrue(r.findings.toString(), r.findings.any { it.contains("wallet Ed25519 signature does not verify") })
    }

    @Test fun walletDigestAndSignatureMismatchesAreRejected() {
        val s = SoftwareReceiptSigner()
        val log = ReceiptLog(logFile, s, keys = registry)
        log.append(walletDraft())
        val one = log.all().single()
        val d1 = JSONObject(log.export(listOf(one))); resign(d1, 0, s) { it.getJSONObject("digests").put("messageSha256", "00".repeat(32)) }
        assertTrue(ReceiptVerifier.verify(d1.toString(), s.publicKeySpki(), emptyList()).findings.any { it.contains("message digest") })
        val d2 = JSONObject(log.export(listOf(one))); resign(d2, 0, s) { it.put("solanaSignature", SignedFixture(seed = 8).signature) }
        assertTrue(ReceiptVerifier.verify(d2.toString(), s.publicKeySpki(), emptyList()).findings.any { it.contains("differs from the wallet signature") })
    }

    @Test fun selfConsistentForgeryUnderAFreshKeyIsOnlyUnpinnedIntegrity() {
        val attacker = SoftwareReceiptSigner()
        val log = ReceiptLog(logFile, attacker, keys = registry)
        log.append(ReceiptDraft(ReceiptKind.NODE, "PAIRED", "invented", "x", "y"))
        val unpinned = ReceiptVerifier.verify(log.export(), null as ByteArray?, emptyList())
        assertTrue(unpinned.accepted)
        assertEquals(ReceiptVerifier.Provenance.UNPINNED_KEY, unpinned.provenance)
        val pinned = ReceiptVerifier.verify(log.export(), SoftwareReceiptSigner().publicKeySpki(), emptyList())
        assertFalse(pinned.accepted)
        assertEquals(ReceiptVerifier.Provenance.KEY_MISMATCH, pinned.provenance)
    }

    @Test fun subsetIsNeverReportedComplete() {
        val s = SoftwareReceiptSigner()
        val log = ReceiptLog(logFile, s, keys = registry)
        repeat(3) { log.append(ReceiptDraft(ReceiptKind.POLICY, "OK", "t$it", "d", "s")) }
        val r = ReceiptVerifier.verify(log.export(log.all().drop(1)), s.publicKeySpki(), emptyList())
        assertTrue(r.findings.toString(), r.accepted)
        assertEquals(ReceiptVerifier.Completeness.SELECTED_SUBSET, r.completeness)
    }

    @Test fun droppingTheTailAndRecomputingTheBundleFailsTheSignedCheckpoint() {
        val s = SoftwareReceiptSigner()
        val log = ReceiptLog(logFile, s, keys = registry)
        repeat(3) { log.append(ReceiptDraft(ReceiptKind.POLICY, "OK", "t$it", "d", "s")) }
        val doc = JSONObject(log.export())
        val arr = doc.getJSONArray("receipts"); arr.remove(2)
        doc.put("bundleSha256", bundleDigest((0 until arr.length()).map { arr.getJSONObject(it).getString("sha256") }))
        val r = ReceiptVerifier.verify(doc.toString(), s.publicKeySpki(), emptyList())
        assertFalse(r.accepted)
        assertTrue(r.findings.toString(), r.findings.any { it.startsWith("Checkpoint") })
        // Relabelling the truncated file as a "selected" export is honest about completeness:
        doc.put("mode", "SELECTED")
        val relabelled = ReceiptVerifier.verify(doc.toString(), s.publicKeySpki(), emptyList())
        assertEquals(ReceiptVerifier.Completeness.SELECTED_SUBSET, relabelled.completeness)
    }

    @Test fun oldRecordsVerifyAfterKeyRotationAndRestart() {
        val first = SoftwareReceiptSigner()
        ReceiptLog(logFile, first, keys = registry).append(ReceiptDraft(ReceiptKind.NODE, "PAIRED", "a", "b", "c"))
        // Restart with a different key (e.g. Keystore became unavailable): a new epoch, not a silent identity swap.
        val second = SoftwareReceiptSigner()
        val log2 = ReceiptLog(logFile, second, keys = registry)
        log2.append(ReceiptDraft(ReceiptKind.NODE, "HEALTH_READ", "a", "b", "c"))
        val all = log2.all()
        assertEquals(listOf(first.keyId(), second.keyId()), all.map { it.signerKeyId })
        val export = log2.export()
        assertEquals(2, JSONObject(export).getJSONArray("deviceKeys").length())
        val r = ReceiptVerifier.verify(export, listOf(first.publicKeySpki(), second.publicKeySpki()), all)
        assertTrue(r.findings.toString(), r.accepted)
        assertEquals(ReceiptVerifier.Completeness.FULL_CHAIN_CHECKPOINTED, r.completeness)
        // Pinning only the new key flags the older epoch rather than hiding it.
        assertFalse(ReceiptVerifier.verify(export, listOf(second.publicKeySpki()), all).accepted)
    }

    @Test fun persistedSoftwareKeyKeepsItsIdentityAcrossRestart() {
        val f = File(tmp.root, "receipts/software-signer.json")
        val a = SoftwareReceiptSigner.persisted(f)
        val b = SoftwareReceiptSigner.persisted(f)
        assertEquals(a.keyId(), b.keyId())
        ReceiptLog(logFile, a, keys = registry).append(ReceiptDraft(ReceiptKind.POLICY, "OK", "t", "d", "s"))
        val log = ReceiptLog(logFile, b, keys = registry)
        assertTrue(ReceiptVerifier.verify(log.export(), b.publicKeySpki(), log.all()).accepted)
    }

    @Test fun truncatedTailIsReportedValidHistoryKeptAndNextAppendLinksToLastValid() {
        val s = SoftwareReceiptSigner()
        val log = ReceiptLog(logFile, s, keys = registry)
        log.append(ReceiptDraft(ReceiptKind.POLICY, "OK", "one", "d", "s"))
        log.append(ReceiptDraft(ReceiptKind.POLICY, "OK", "two", "d", "s"))
        logFile.appendText("{\"sha256\":\"abc\",\"body\":\"{\\\"schema\\\":") // crash mid-append
        val restarted = ReceiptLog(logFile, s, keys = registry)
        val read = restarted.read()
        assertEquals(2, read.receipts.size)
        assertEquals(1, read.damaged.size)
        assertTrue(read.damaged.single().reason.contains("Truncated"))
        val third = restarted.append(ReceiptDraft(ReceiptKind.POLICY, "OK", "three", "d", "s"))
        assertEquals(3L, third.sequence)
        assertEquals(read.receipts.last().sha256, third.json.getString("previousSha256"))
        val after = restarted.read()
        assertEquals(3, after.receipts.size)
        assertEquals(1, after.damaged.size) // the damaged bytes are kept and still reported
        val r = ReceiptVerifier.verify(restarted.export(), s.publicKeySpki(), after.receipts)
        assertFalse("a log with damage must not verify as complete", r.accepted)
        assertTrue(r.findings.any { it.contains("damaged log line") })
    }

    @Test fun modifiedLineInTheMiddleIsReportedNotDropped() {
        val s = SoftwareReceiptSigner()
        val log = ReceiptLog(logFile, s, keys = registry)
        repeat(3) { log.append(ReceiptDraft(ReceiptKind.POLICY, "OK", "t$it", "d", "s")) }
        val lines = logFile.readLines().toMutableList()
        lines[1] = lines[1].replace("\\\"title\\\":\\\"t1\\\"", "\\\"title\\\":\\\"tX\\\"").also { check(it != lines[1]) }
        logFile.writeText(lines.joinToString("\n") + "\n")
        val r = ReceiptLog(logFile, s, keys = registry).read()
        assertEquals(2, r.receipts.size)
        assertEquals(2, r.damaged.single().lineNumber)
    }

    @Test fun impossibleCombinationsAndMalformedFieldsAreRejected() {
        val s = SoftwareReceiptSigner()
        val log = ReceiptLog(logFile, s, keys = registry)
        log.append(ReceiptDraft(ReceiptKind.REVIEW, "REFUSED", "r", "d", "s", "devnet", lamports = 10))
        val one = log.all().single()
        val cases = mapOf<String, (JSONObject) -> Unit>(
            "refused review cannot carry" to { b -> b.put("payment", "OBSERVED_FINALIZED").put("solanaSignature", fx.signature) },
            "claims a chain outcome without a signature" to { b -> b.put("outcome", "SUBMITTED").put("payment", "SUBMITTED_NOT_CONFIRMED") },
            "not a 64-byte base58" to { b -> b.put("solanaSignature", "5Kb") },
            "negative amount" to { b -> b.put("lamports", -1) },
            "missing required field" to { b -> b.remove("payment") },
            "unknown kind" to { b -> b.put("kind", "MINING_REWARD") },
            "only wallet reviews" to { b -> b.put("kind", "NODE").put("outcome", "HEALTH_READ").put("payment", "OBSERVED_CONFIRMED").put("solanaSignature", fx.signature) },
        )
        for ((expect, edit) in cases) {
            val doc = JSONObject(log.export(listOf(one)))
            resign(doc, 0, s, edit)
            val r = ReceiptVerifier.verify(doc.toString(), s.publicKeySpki(), emptyList())
            assertFalse(expect, r.accepted)
            assertTrue("$expect -> ${r.findings}", r.findings.any { it.contains(expect) })
        }
    }

    @Test fun missingKeyEpochIsReported() {
        val a = SoftwareReceiptSigner()
        ReceiptLog(logFile, a, keys = registry).append(ReceiptDraft(ReceiptKind.POLICY, "OK", "t", "d", "s"))
        val b = SoftwareReceiptSigner()
        val log = ReceiptLog(logFile, b, keys = registry)
        val doc = JSONObject(log.export())
        val keys = doc.getJSONArray("deviceKeys")
        for (i in keys.length() - 1 downTo 0) if (keys.getJSONObject(i).getString("keyId") == a.keyId()) keys.remove(i)
        val r = ReceiptVerifier.verify(doc.toString(), null as ByteArray?, emptyList())
        assertFalse(r.accepted)
        assertTrue(r.findings.any { it.contains("not included in the export") })
    }

    @Test fun claimGuardFromFeatureBranchStillApplies() {
        assertTrue(com.edgeore.app.receipts.chainClaimContradictsSignature("OBSERVED_CONFIRMED", false, ""))
        assertFalse(com.edgeore.app.receipts.chainClaimContradictsSignature("NOT_OBSERVED", false, ""))
    }

    @Test fun evidenceDefaultsAreNotPasses() {
        assertEquals("NOT_AVAILABLE", Evidence.NOT_AVAILABLE.state)
    }

    // ---- H4: signed envelope over the descriptive export fields (additive; schema and receipt lines unchanged) ----
    private fun envelopeExport(): Pair<SoftwareReceiptSigner, String> {
        val s = SoftwareReceiptSigner()
        val log = ReceiptLog(logFile, s, keys = registry)
        log.append(ReceiptDraft(ReceiptKind.POLICY, "REFUSED", "Fixture", "Synthetic", "unit test"))
        return s to log.export()
    }

    @Test fun signedEnvelopeVerifiesAndIsReported() {
        val (s, json) = envelopeExport()
        val doc = JSONObject(json)
        assertEquals(ReceiptLog.ENVELOPE_DOMAIN, doc.getJSONObject("envelope").getString("domain"))
        assertEquals("edgeore.receipt-export.v2", doc.getString("schema"))
        val r = ReceiptVerifier.verify(json, s.publicKeySpki(), emptyList())
        assertTrue(r.findings.toString(), r.accepted)
        assertTrue(r.descriptiveFieldsSigned)
        assertTrue(r.summary.contains("descriptive fields covered by the signed envelope"))
    }

    @Test fun editingAnyDescriptiveFieldBreaksTheEnvelope() {
        val (s, json) = envelopeExport()
        val edits: List<(JSONObject) -> Unit> = listOf(
            { d -> d.put("note", "Edited after export") },
            { d -> d.put("payment", "OBSERVED 9 SOL") },
            { d -> d.put("location", "Somewhere") },
            { d -> d.getJSONArray("exclusions").put("added") },
            { d -> d.getJSONArray("deviceKeys").getJSONObject(0).put("protection", "Android Keystore (StrongBox)") },
            { d -> d.getJSONArray("deviceKeys").getJSONObject(0).put("firstUsedAt", "2020-01-01T00:00:00Z") },
        )
        for ((i, edit) in edits.withIndex()) {
            val doc = JSONObject(json).also(edit)
            val r = ReceiptVerifier.verify(doc.toString(), s.publicKeySpki(), emptyList())
            assertFalse("edit #$i accepted", r.accepted)
            assertTrue("edit #$i: ${r.findings}", r.findings.any { it.startsWith("Envelope:") })
        }
    }

    @Test fun envelopeSignedByAnotherKeyIsRejected() {
        val (s, json) = envelopeExport()
        val doc = JSONObject(json)
        val other = SoftwareReceiptSigner()
        val env = doc.getJSONObject("envelope")
        env.put("signature", Base64.getEncoder().encodeToString(other.sign(com.edgeore.app.receipts.envelopePayload(doc).toByteArray())))
        val r = ReceiptVerifier.verify(doc.toString(), s.publicKeySpki(), emptyList())
        assertFalse(r.accepted)
        assertTrue(r.findings.toString(), r.findings.contains("Envelope: signature invalid"))
    }

    @Test fun strippedEnvelopeIsALabelledDowngradeNotASignedResult() {
        // Older exports have no envelope. Removing it is therefore accepted, but never reported as signed.
        val (s, json) = envelopeExport()
        val doc = JSONObject(json).also { it.remove("envelope") }.put("note", "Edited")
        val r = ReceiptVerifier.verify(doc.toString(), s.publicKeySpki(), emptyList())
        assertTrue(r.findings.toString(), r.accepted)
        assertFalse(r.descriptiveFieldsSigned)
        assertTrue(r.summary.contains("descriptive fields not signed (no envelope)"))
    }
}
