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
    @Test fun reportsMissingInputAsToolError() { assertEquals(2, run(java.io.File(temp.root, "missing").path).first) }
    @Test fun rejectsWrongArguments() { assertEquals(2, run().first); assertEquals(2, run("file", "--wrong", "key").first) }
}
