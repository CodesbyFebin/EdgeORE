import com.edgeore.app.receipts.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class ReceiptCheckerTest {
    @get:Rule val temp = TemporaryFolder()
    private fun run(vararg args: String): Pair<Int, String> {
        val bytes = ByteArrayOutputStream()
        val stream = PrintStream(bytes)
        return checkReceipt(arrayOf(*args), stream, stream) to bytes.toString("UTF-8")
    }
    private fun export(): Pair<java.io.File, SoftwareReceiptSigner> {
        val signer = SoftwareReceiptSigner()
        val log = ReceiptLog(java.io.File(temp.root, "log.jsonl"), signer)
        log.append(ReceiptDraft(ReceiptKind.POLICY, "REFUSED", "Fixture", "Synthetic test only", "unit test"))
        val file = temp.newFile("export.json").apply { writeText(log.export()) }
        return file to signer
    }
    @Test fun acceptsExportAndReportsUnpinnedOfflineLimits() {
        val (file, _) = export()
        val (code, output) = run(file.path)
        assertEquals(0, code)
        assertTrue(output.contains("VERIFIED: PASS"))
        assertTrue(output.contains("not pinned"))
        assertTrue(output.contains("chain status not checked offline"))
    }
    @Test fun rejectsWellFormedBodyTamperAndPreservesOriginal() {
        val (original, _) = export()
        val before = original.readBytes()
        val doc = JSONObject(original.readText())
        val record = doc.getJSONArray("receipts").getJSONObject(0)
        val body = JSONObject(record.getString("body")).put("title", "Changed")
        record.put("body", body.toString())
        val copy = temp.newFile("tampered.json").apply { writeText(doc.toString()) }
        assertEquals(1, run(copy.path).first)
        assertArrayEquals(before, original.readBytes())
        assertEquals(0, run(original.path).first)
    }
    @Test fun acceptsPinnedKeyAndRejectsDifferentKey() {
        val (file, signer) = export()
        val key = temp.newFile("key.der").apply { writeBytes(signer.publicKeySpki()) }
        assertEquals(0, run(file.path, "--trusted-key", key.path).first)
        key.writeBytes(SoftwareReceiptSigner().publicKeySpki())
        assertEquals(1, run(file.path, "--trusted-key", key.path).first)
    }
    @Test fun rejectsMalformedJson() {
        val file = temp.newFile("bad.json").apply { writeText("{") }
        assertEquals(1, run(file.path).first)
    }
    @Test fun rejectsMalformedUtf8() {
        val file = temp.newFile("bad-utf8.json").apply { writeBytes(byteArrayOf(0xc3.toByte(), 0x28)) }
        assertEquals(1, run(file.path).first)
    }
    @Test fun acceptsStorageReceiptAndRejectsItsTamperedCopy() {
        // STORAGE was added by feature/storage-vault; the checker compiles the app's Receipts.kt, so it must accept the kind.
        val log = ReceiptLog(java.io.File(temp.root, "storage.jsonl"), SoftwareReceiptSigner())
        log.append(ReceiptDraft(ReceiptKind.STORAGE, "IMPORTED", "Imported", "Fixture. App-record integrity evidence; not proof of provider storage or physical deletion.",
            "EdgeORE local vault", operationId = "op-fixture", digests = mapOf("encryptedObjectSha256" to "00".repeat(32), "vaultObjectId" to "fixture"),
            localObservation = Evidence("OBSERVED", "Synthetic test only")))
        val file = temp.newFile("storage-export.json").apply { writeText(log.export()) }
        val (code, output) = run(file.path)
        assertEquals(output, 0, code)
        assertTrue(output.contains("VERIFIED: PASS"))
        val doc = JSONObject(file.readText())
        val record = doc.getJSONArray("receipts").getJSONObject(0)
        record.put("body", JSONObject(record.getString("body")).put("outcome", "DELETED").toString())
        val copy = temp.newFile("storage-tampered.json").apply { writeText(doc.toString()) }
        assertEquals(1, run(copy.path).first)
    }
    @Test fun rejectsSignedStorageReceiptThatClaimsPayment() {
        // Correctly signed, so only the schema rule can reject it: a storage/backup record is never a payment observation.
        val log = ReceiptLog(java.io.File(temp.root, "storage-pay.jsonl"), SoftwareReceiptSigner())
        log.append(ReceiptDraft(ReceiptKind.STORAGE, "PINNED", "Pinned", "Fixture", "EdgeORE local vault", payment = "OBSERVED fixture"))
        val file = temp.newFile("storage-pay.json").apply { writeText(log.export()) }
        val (code, output) = run(file.path)
        assertEquals(1, code)
        assertTrue(output, output.contains("only wallet reviews can carry a payment observation"))
    }
    @Test fun signedEnvelopeDetectsEditedDescriptiveFields() {
        // H4: exports now carry a signed envelope, so editing note or a top-level payment string is rejected.
        val (file, _) = export()
        val (okCode, okOut) = run(file.path)
        assertEquals(okOut, 0, okCode)
        assertTrue(okOut, okOut.contains("Descriptive export fields covered by the signed envelope"))
        val doc = JSONObject(file.readText()).put("note", "Edited after export").put("payment", "OBSERVED 9 SOL")
        val copy = temp.newFile("edited-note.json").apply { writeText(doc.toString()) }
        val (code, output) = run(copy.path)
        assertEquals(output, 1, code)
        assertTrue(output, output.contains("Envelope: descriptive fields changed after export"))
    }
    @Test fun exportWithoutEnvelopeIsLabelledUnsigned() {
        // A pre-0.2.9 export (or one with the envelope stripped) still verifies its receipts, but is labelled unsigned.
        val (file, _) = export()
        val doc = JSONObject(file.readText()).also { it.remove("envelope") }.put("note", "Edited after export")
        val copy = temp.newFile("no-envelope.json").apply { writeText(doc.toString()) }
        val (code, output) = run(copy.path)
        assertEquals(output, 0, code)
        assertTrue(output, output.contains("Not covered by any signature (descriptive only, no signed envelope): top-level note, exclusions, payment, location, exportedAt"))
    }
    @Test fun reportsMissingInputAsToolError() { assertEquals(2, run(java.io.File(temp.root, "missing").path).first) }
    @Test fun rejectsWrongArguments() { assertEquals(2, run().first); assertEquals(2, run("file", "--wrong", "key").first) }
}
