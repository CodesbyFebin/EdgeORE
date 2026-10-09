package com.edgeore.verifier

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.util.Base64

class VerifyReceiptCliTest {
    @get:Rule val tmp = TemporaryFolder()

    private class Run(val exit: Int, val out: String, val err: String)

    private fun cli(vararg args: String): Run {
        val o = ByteArrayOutputStream(); val e = ByteArrayOutputStream()
        val code = VerifyReceiptCli.run(arrayOf(*args), PrintStream(o, true, "UTF-8"), PrintStream(e, true, "UTF-8"))
        return Run(code, o.toString("UTF-8"), e.toString("UTF-8"))
    }

    private fun exportFile(name: String = "receipt.json"): Pair<File, Fixtures.Export> {
        val x = Fixtures.export(tmp.newFolder())
        return File(tmp.root, name).apply { writeText(x.json) } to x
    }

    /** Copies [src] and overwrites the single byte at [offset] (like `dd conv=notrunc`), leaving the original alone. */
    private fun tamperedCopy(src: File, offset: Int): File {
        val bytes = src.readBytes()
        val t = File(src.parentFile, "tampered-" + src.name)
        t.writeBytes(bytes.copyOf().also { it[offset] = if (it[offset] == 'X'.code.toByte()) 'Y'.code.toByte() else 'X'.code.toByte() })
        return t
    }

    @Test fun realExportPasses() {
        val (f, _) = exportFile()
        val r = cli(f.path)
        assertEquals(r.out, 0, r.exit)
        assertTrue(r.out, r.out.startsWith("PASS "))
        assertTrue(r.out, r.out.contains("Integrity OK for 2 receipt(s)"))
        assertTrue(r.out, r.out.contains("wallet signatures verified: 1"))
        assertTrue(r.out, r.out.contains("full chain through signed checkpoint"))
        assertTrue(r.out, r.out.contains("chain status not checked offline"))
    }

    @Test fun oneByteInsideAReceiptBodyFailsAndTheOriginalStillPasses() {
        val (f, _) = exportFile()
        val original = f.readBytes()
        val offset = f.readText().indexOf("Signed transfer") + 2 // a character of the signed receipt body
        assertTrue(offset > 2)
        val t = tamperedCopy(f, offset)
        val r = cli(t.path)
        assertEquals(r.out, 1, r.exit)
        assertTrue(r.out, r.out.startsWith("FAIL "))
        assertTrue(r.out, r.out.contains("[hash mismatch]"))
        assertArrayEquals(original, f.readBytes())
        assertEquals(0, cli(f.path).exit)
    }

    @Test fun oneByteInADeviceSignatureFails() {
        val (f, _) = exportFile()
        val text = f.readText()
        val sig = JSONObject(text).getJSONArray("receipts").getJSONObject(0).getString("deviceSignature")
        val r = cli(tamperedCopy(f, text.indexOf(sig) + 10).path)
        assertEquals(r.out, 1, r.exit)
        assertTrue(r.out, r.out.contains("[signature invalid]") || r.out.contains("[malformed]"))
    }

    @Test fun oneByteInTheCheckpointFails() {
        val (f, _) = exportFile()
        val text = f.readText()
        val cpSig = JSONObject(text).getJSONObject("checkpoint").getString("signature")
        val r = cli(tamperedCopy(f, text.indexOf(cpSig) + 10).path)
        assertEquals(r.out, 1, r.exit)
        assertTrue(r.out, r.out.contains("Checkpoint"))
    }

    @Test fun walletSignatureSwapFails() {
        val (f, x) = exportFile()
        val text = f.readText()
        // Replace one base58 character of the wallet signature everywhere it appears (body + wallet block),
        // then the body digest no longer matches either.
        val i = text.indexOf(x.walletSignature) + 5
        val r = cli(tamperedCopy(f, i).path)
        assertEquals(r.out, 1, r.exit)
    }

    @Test fun truncatedJsonFails() {
        val (f, _) = exportFile()
        val t = File(tmp.root, "truncated.json").apply { writeBytes(f.readBytes().copyOf(f.length().toInt() / 2)) }
        val r = cli(t.path)
        assertEquals(r.out, 1, r.exit)
        assertTrue(r.out, r.out.contains("Not a JSON document"))
    }

    @Test fun malformedEmptyAndMissingFilesFail() {
        val garbage = tmp.newFile("garbage.json").apply { writeText("{\"schema\": \"edgeore.receipt-export.v2\", ") }
        val empty = tmp.newFile("empty.json")
        val wrongSchema = tmp.newFile("other.json").apply { writeText("{\"schema\":\"something-else\",\"receipts\":[]}") }
        for (f in listOf(garbage.path, empty.path, wrongSchema.path, File(tmp.root, "absent.json").path)) {
            val r = cli(f)
            assertEquals("$f: ${r.out}", 1, r.exit)
            assertTrue(r.out, r.out.startsWith("FAIL "))
        }
    }

    @Test fun removedReceiptFails() {
        val (f, _) = exportFile()
        val doc = JSONObject(f.readText())
        doc.getJSONArray("receipts").remove(0)
        val r = cli(File(tmp.root, "removed.json").apply { writeText(doc.toString(2)) }.path)
        assertEquals(r.out, 1, r.exit)
    }

    @Test fun anyFailMakesTheRunFailButEveryFileIsReported() {
        val (f, _) = exportFile()
        val t = tamperedCopy(f, f.readText().indexOf("Signed transfer") + 2)
        val r = cli(f.path, t.path)
        assertEquals(1, r.exit)
        assertTrue(r.out, r.out.contains("PASS ${f.path}"))
        assertTrue(r.out, r.out.contains("FAIL ${t.path}"))
        assertTrue(r.out, r.out.contains("RESULT: 1 of 2 file(s) FAIL"))
    }

    @Test fun pinnedKeyMatchesAndAForeignKeyIsRejected() {
        val (f, x) = exportFile()
        val good = Base64.getEncoder().encodeToString(x.signer.publicKeySpki())
        val ok = cli("--trusted-key", good, f.path)
        assertEquals(ok.out, 0, ok.exit)
        assertTrue(ok.out, ok.out.contains("matches pinned key"))
        val other = Base64.getEncoder().encodeToString(com.edgeore.app.receipts.SoftwareReceiptSigner().publicKeySpki())
        val bad = cli("--trusted-key", other, f.path)
        assertEquals(bad.out, 1, bad.exit)
        assertTrue(bad.out, bad.out.contains("does NOT match pinned key"))
    }

    /** Every single-byte change anywhere inside a signed receipt body is rejected (checked in-process, byte by byte). */
    @Test fun everyByteOfEverySignedBodyIsProtected() {
        val (f, _) = exportFile()
        val bytes = f.readBytes()
        val text = String(bytes, Charsets.US_ASCII)
        val ranges = Regex("\"body\": \"((?:[^\"\\\\]|\\\\.)*)\"").findAll(text).map { it.groups[1]!!.range }.toList()
        assertEquals(2, ranges.size)
        var checked = 0
        for (r in ranges) for (i in r) {
            val t = bytes.copyOf().also { it[i] = if (it[i] == 'X'.code.toByte()) 'Y'.code.toByte() else 'X'.code.toByte() }
            val copy = File(tmp.root, "sweep.json").apply { writeBytes(t) }
            val res = VerifyReceiptCli.check(copy, null)
            assertTrue("byte $i (${text[i]}) changed but still passed", !res.pass)
            checked++
        }
        assertTrue(checked > 500)
    }

    /**
     * Known limitation of the existing verifier, recorded so nobody claims otherwise: descriptive export fields
     * outside the receipts and checkpoint (here the "note") are not signed, so editing them is not detected.
     */
    @Test fun knownLimitationUnsignedExportNoteIsNotDetected() {
        val (f, _) = exportFile()
        val text = f.readText()
        val i = text.indexOf("Device-key signatures show") + 3
        val r = cli(tamperedCopy(f, i).path)
        assertEquals(r.out, 0, r.exit)
    }

    @Test fun usageErrorsExitTwo() {
        assertEquals(2, cli().exit)
        assertEquals(2, cli("--trusted-key").exit)
        assertEquals(2, cli("--bogus", "x.json").exit)
    }
}
