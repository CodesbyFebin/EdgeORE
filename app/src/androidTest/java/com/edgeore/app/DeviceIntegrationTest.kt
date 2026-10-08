package com.edgeore.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.edgeore.app.crypto.Ed25519
import com.edgeore.app.device.KeystoreReceiptSigner
import com.edgeore.app.node.NodeAgentClient
import com.edgeore.app.node.NodeAgentProtocol
import com.edgeore.app.node.NodeResult
import com.edgeore.app.receipts.Evidence
import com.edgeore.app.receipts.ReceiptDraft
import com.edgeore.app.receipts.ReceiptKind
import com.edgeore.app.receipts.ReceiptLog
import com.edgeore.app.receipts.ReceiptVerifier
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Base64

@RunWith(AndroidJUnit4::class)
class DeviceIntegrationTest {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun keystoreSignedReceiptsVerifyAndTamperIsRejected() {
        val signer = KeystoreReceiptSigner("edgeore.test.receipts")
        val file = File(ctx.cacheDir, "it-receipts.jsonl").apply { delete() }
        val log = ReceiptLog(file, signer)
        log.append(ReceiptDraft(ReceiptKind.POLICY, "TEST", "a", "b", "device", localObservation = Evidence("CHECKED", "test")))
        log.append(ReceiptDraft(ReceiptKind.REVIEW, "REFUSED", "c", "d", "device"))
        val export = log.export()
        assertTrue(ReceiptVerifier.verify(export, signer.publicKeySpki(), log.all()).accepted)
        val doc = JSONObject(export)
        val e = doc.getJSONArray("receipts").getJSONObject(1)
        e.put("body", e.getString("body").replace("REFUSED", "SUBMITTED"))
        assertFalse(ReceiptVerifier.verify(doc.toString(), signer.publicKeySpki(), log.all()).accepted)
    }

    /** Real DeProof node agent reached through `adb reverse`; skipped without instrumentation args. */
    @Test fun pairObserveRevokeOverAndroidTls() {
        val args = InstrumentationRegistry.getArguments()
        val endpoint = args.getString("nodeEndpoint").orEmpty()
        assumeTrue("node args not provided", endpoint.isNotEmpty())
        val pin = args.getString("nodeCert")!!
        val challenge = NodeAgentProtocol.parseChallenge(String(Base64.getDecoder().decode(args.getString("nodeChallengeB64")!!)))
        val code = args.getString("nodeCode")!!
        val client = NodeAgentClient(endpoint, pin)
        val key = Ed25519.generate()
        val paired = client.pair(challenge, code, listOf("READ_NODE"), key)
        assertTrue("$paired", paired is NodeResult.Ok)
        val session = (paired as NodeResult.Ok).value
        val health = client.command(session, key, "observe")
        assertTrue("$health", health is NodeResult.Ok)
        assertEquals(challenge.fingerprint, (health as NodeResult.Ok).value.getString("fingerprint"))
        assertTrue(client.command(session, key, "revoke") is NodeResult.Ok)
        val after = client.command(session, key, "observe")
        assertTrue("$after", after is NodeResult.Refused && after.code == "SESSION_REVOKED_OR_UNKNOWN")
        android.util.Log.i("EdgeOreIT", "Android TLS node journey PASSED: " + health.value)
    }
}
