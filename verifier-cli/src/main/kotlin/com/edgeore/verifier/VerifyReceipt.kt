package com.edgeore.verifier

import com.edgeore.app.io.BoundedInput
import com.edgeore.app.io.InputTooLargeException
import com.edgeore.app.receipts.ReceiptVerifier
import java.io.File
import java.io.FileInputStream
import java.io.PrintStream
import java.util.Base64
import kotlin.system.exitProcess

/**
 * Command-line front end for the app's own [ReceiptVerifier] (compiled from app/src/main by reference).
 *
 * It checks exported receipt files offline: body digests, device-key signatures, chain links, bundle digest,
 * the signed checkpoint and any wallet Ed25519 signature carried in a receipt. It does NOT query Solana RPC,
 * so it says nothing about whether a transaction landed on chain.
 *
 * Exit codes: 0 = every file PASS, 1 = at least one FAIL, 2 = usage error.
 */
object VerifyReceiptCli {
    /** Same limit and decoding as the app's "Verify offline" import (EdgeOreViewModel). */
    const val MAX_BYTES = 4_000_000L

    const val USAGE = """Usage: verify-receipt.sh [--trusted-key <base64 SPKI>]... <receipt-export.json> [more.json ...]

Verifies EdgeORE receipt exports offline with the app's own ReceiptVerifier.
Checks: receipt body SHA-256, device-key signatures, chain links and sequence, bundle digest,
signed checkpoint (full-chain exports) and wallet Ed25519 signatures carried in receipts.
Does NOT query Solana RPC: on-chain confirmation is not checked.

  --trusted-key B64   Pin a device public key (SubjectPublicKeyInfo, base64) obtained through another
                      channel. Repeatable. Without it, a PASS only shows the file is consistent with the
                      keys it carries itself (provenance: not pinned).

Exit status: 0 if every file passes, 1 if any file fails, 2 on usage error."""

    fun run(args: Array<String>, out: PrintStream, err: PrintStream): Int {
        val files = mutableListOf<String>()
        val trusted = mutableListOf<ByteArray>()
        var i = 0
        while (i < args.size) {
            when (val a = args[i]) {
                "-h", "--help" -> { out.println(USAGE); return 0 }
                "--trusted-key" -> {
                    val v = args.getOrNull(i + 1) ?: run { err.println("--trusted-key needs a value\n\n$USAGE"); return 2 }
                    val key = runCatching { Base64.getDecoder().decode(v.trim()) }.getOrNull()
                    if (key == null || key.isEmpty()) { err.println("--trusted-key is not base64: $v"); return 2 }
                    trusted += key; i++
                }
                "--" -> { files += args.drop(i + 1); i = args.size }
                else -> if (a.startsWith("--")) { err.println("Unknown option $a\n\n$USAGE"); return 2 } else files += a
            }
            i++
        }
        if (files.isEmpty()) { err.println(USAGE); return 2 }

        var failed = 0
        for (path in files) {
            val r = check(File(path), trusted.takeIf { it.isNotEmpty() })
            if (r.pass) out.println("PASS $path") else { out.println("FAIL $path"); failed++ }
            out.println("  ${r.summary}")
            r.reasons.forEach { out.println("  reason: $it") }
        }
        out.println(if (failed == 0) "RESULT: ${files.size} of ${files.size} file(s) PASS" else "RESULT: $failed of ${files.size} file(s) FAIL")
        return if (failed == 0) 0 else 1
    }

    data class FileResult(val pass: Boolean, val summary: String, val reasons: List<String>)

    fun check(file: File, trusted: List<ByteArray>?): FileResult {
        if (!file.isFile) return FileResult(false, "REJECTED", listOf("[unreadable] file not found or not a regular file"))
        val text = try {
            FileInputStream(file).use { BoundedInput.readAtMost(it, MAX_BYTES) }.toString(Charsets.UTF_8)
        } catch (_: InputTooLargeException) {
            return FileResult(false, "REJECTED", listOf("[unreadable] larger than $MAX_BYTES bytes (the app's import limit)"))
        } catch (e: Exception) {
            return FileResult(false, "REJECTED", listOf("[unreadable] ${e.message ?: e.javaClass.simpleName}"))
        }
        val report = try {
            ReceiptVerifier.verify(text, trusted, emptyList())
        } catch (e: Exception) {
            // The verifier is written to return findings; an exception is still a rejection, never a pass.
            return FileResult(false, "REJECTED", listOf("[malformed] verifier error: ${e.javaClass.simpleName}: ${e.message}"))
        }
        val reasons = report.findings.map { "[${category(it)}] $it" }
        return FileResult(report.accepted, report.summary, if (!report.accepted && reasons.isEmpty()) listOf("[malformed] no receipt verified") else reasons)
    }

    /** A coarse label for a verifier finding. The finding text itself is printed unchanged. */
    fun category(finding: String): String {
        val f = finding.lowercase()
        return when {
            "sha-256 does not match" in f -> "hash mismatch"
            "claims a chain outcome" in f -> "false chain claim"
            "not included in the export" in f || "not present in the export" in f -> "key missing"
            "wallet" in f || "transaction signature differs" in f -> "wallet evidence mismatch"
            "pinned" in f || "different device key" in f -> "key not pinned"
            "checkpoint" in f -> "checkpoint invalid"
            "signature invalid" in f -> "signature invalid"
            "chain" in f || "link" in f || "sequence" in f || "bundle digest" in f -> "chain broken"
            "differs from the copy" in f -> "local copy mismatch"
            else -> "malformed"
        }
    }
}

fun main(args: Array<String>) {
    exitProcess(VerifyReceiptCli.run(args, System.out, System.err))
}
