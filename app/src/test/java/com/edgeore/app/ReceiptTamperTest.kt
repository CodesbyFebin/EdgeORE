package com.edgeore.app

import com.edgeore.app.crypto.Sha256
import com.edgeore.app.receipts.Evidence
import com.edgeore.app.receipts.ReceiptDraft
import com.edgeore.app.receipts.ReceiptKind
import com.edgeore.app.receipts.ReceiptLog
import com.edgeore.app.receipts.ReceiptVerifier
import com.edgeore.app.receipts.SoftwareReceiptSigner
import com.edgeore.app.receipts.bundleDigest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Base64

class ReceiptTamperTest {
    @get:Rule val tmp = TemporaryFolder()
    private val signer = SoftwareReceiptSigner()

    private fun logWithThree(): ReceiptLog {
        val log = ReceiptLog(tmp.newFile("r.jsonl").also { it.delete() }, signer)
        log.append(ReceiptDraft(ReceiptKind.NODE, "PAIRED", "Node paired", "READ_NODE", "https://127.0.0.1:9843", localObservation = Evidence("CHECKED", "pinned TLS")))
        log.append(ReceiptDraft(ReceiptKind.REVIEW, "REFUSED", "Review refused", "user", "review", "devnet", lamports = 1000))
        log.append(ReceiptDraft(ReceiptKind.LOCAL_AI, "COMPLETED_ON_OWNED_HOST", "AI", "digests only", "http://127.0.0.1:11434", digests = mapOf("promptSha256" to Sha256.hex("x"))))
        return log
    }

    private fun mutate(export: String, block: (JSONObject) -> Unit): String = JSONObject(export).also(block).toString()

    @Test fun untouchedExportAccepted() {
        val log = logWithThree()
        val r = ReceiptVerifier.verify(log.export(), signer.publicKeySpki(), log.all())
        assertTrue(r.findings.toString(), r.accepted)
        assertEquals(3, r.verifiedReceipts)
    }

    @Test fun chainLinksAndSequence() {
        val all = logWithThree().all()
        assertEquals(ReceiptLog.GENESIS, all[0].json.getString("previousSha256"))
        assertEquals(all[0].sha256, all[1].json.getString("previousSha256"))
        assertEquals(listOf(1L, 2L, 3L), all.map { it.sequence })
        assertTrue(all.all { it.integrityOk })
    }

    @Test fun editedBodyRejected() {
        val log = logWithThree()
        val t = mutate(log.export()) { d ->
            val e = d.getJSONArray("receipts").getJSONObject(1)
            e.put("body", e.getString("body").replace("REFUSED", "SUBMITTED"))
        }
        val r = ReceiptVerifier.verify(t, signer.publicKeySpki(), log.all())
        assertFalse(r.accepted)
        assertTrue(r.findings.any { it.contains("does not match body") })
    }

    @Test fun editedBodyWithRecomputedDigestRejectedBySignature() {
        val log = logWithThree()
        val t = mutate(log.export()) { d ->
            val arr = d.getJSONArray("receipts")
            val e = arr.getJSONObject(1)
            val body = e.getString("body").replace("REFUSED", "SUBMITTED")
            e.put("body", body).put("sha256", Sha256.hex(body))
            d.put("bundleSha256", bundleDigest((0 until arr.length()).map { arr.getJSONObject(it).getString("sha256") }))
        }
        val r = ReceiptVerifier.verify(t, signer.publicKeySpki(), log.all())
        assertFalse(r.accepted)
        assertTrue(r.findings.any { it.contains("device signature invalid") })
    }

    @Test fun reSignedWithAnotherKeyRejected() {
        val log = logWithThree()
        val attacker = SoftwareReceiptSigner()
        val t = mutate(log.export()) { d ->
            val arr = d.getJSONArray("receipts")
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                e.put("deviceSignature", Base64.getEncoder().encodeToString(attacker.sign(e.getString("sha256").toByteArray())))
            }
            d.getJSONObject("deviceKey").put("publicKeySpkiBase64", Base64.getEncoder().encodeToString(attacker.publicKeySpki()))
        }
        val r = ReceiptVerifier.verify(t, signer.publicKeySpki(), emptyList())
        assertFalse(r.accepted)
        assertTrue(r.findings.any { it.contains("different device key") })
    }

    @Test fun removedReceiptRejectedByBundleDigest() {
        val log = logWithThree()
        val t = mutate(log.export()) { d -> d.getJSONArray("receipts").remove(1) }
        val r = ReceiptVerifier.verify(t, signer.publicKeySpki(), log.all())
        assertFalse(r.accepted)
        assertTrue(r.findings.any { it.contains("Bundle digest") })
    }

    @Test fun reorderedReceiptsRejected() {
        val log = logWithThree()
        val t = mutate(log.export()) { d ->
            val arr = d.getJSONArray("receipts")
            val a = arr.getJSONObject(0); val b = arr.getJSONObject(1)
            arr.put(0, b); arr.put(1, a)
        }
        assertFalse(ReceiptVerifier.verify(t, signer.publicKeySpki(), log.all()).accepted)
    }

    @Test fun localCopyMismatchRejected() {
        val log = logWithThree()
        val other = ReceiptLog(tmp.newFile("o.jsonl").also { it.delete() }, signer)
        other.append(ReceiptDraft(ReceiptKind.NODE, "PAIRED", "x", "y", "z"))
        // Same signer, valid digest, but this device's stored copy for that id differs.
        val forged = JSONObject(other.export())
        val entry = forged.getJSONArray("receipts").getJSONObject(0)
        val localId = log.all()[0].id
        val body = JSONObject(entry.getString("body")).put("id", localId).toString()
        val sha = Sha256.hex(body)
        entry.put("body", body).put("sha256", sha).put("deviceSignature", Base64.getEncoder().encodeToString(signer.sign(sha.toByteArray())))
        forged.put("bundleSha256", bundleDigest(listOf(sha)))
        val r = ReceiptVerifier.verify(forged.toString(), signer.publicKeySpki(), log.all())
        assertFalse(r.accepted)
        assertTrue(r.findings.any { it.contains("differs from the copy stored") })
    }

    @Test fun garbageAndEmptyRejected() {
        assertFalse(ReceiptVerifier.verify("not json", null).accepted)
        assertFalse(ReceiptVerifier.verify("{\"schema\":\"other\"}", null).accepted)
        val empty = ReceiptLog(tmp.newFile("e.jsonl").also { it.delete() }, signer)
        assertFalse(ReceiptVerifier.verify(empty.export(), signer.publicKeySpki()).accepted)
    }

    @Test fun singleReceiptExportVerifies() {
        val log = logWithThree()
        val one = log.all()[2]
        val r = ReceiptVerifier.verify(log.export(listOf(one)), signer.publicKeySpki(), log.all())
        assertTrue(r.findings.toString(), r.accepted)
    }

    @Test fun exportListsExclusionsAndNoContents() {
        val log = logWithThree()
        val doc = JSONObject(log.export())
        assertEquals(ReceiptLog.EXCLUSIONS.size, doc.getJSONArray("exclusions").length())
        assertFalse(doc.toString().contains("privateKey"))
    }
}
