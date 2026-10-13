package com.edgeore.app

import com.edgeore.app.crypto.Sha256
import com.edgeore.app.storage.BackupUnavailableException
import com.edgeore.app.storage.OwnedGatewayBackupProvider
import com.edgeore.app.storage.ProviderObservation
import com.edgeore.app.storage.UploadRequest
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Runs the real OwnedGatewayBackupProvider against services/storage-gateway behind a Caddy HTTPS proxy with a
 * self-signed certificate, started by scripts/gateway-proxy-it.sh. Skipped (not failed) when EDGEORE_GATEWAY_IT is unset.
 * The config file (endpoint, SPKI pin, token, quota) lives in a temporary directory and is never committed.
 */
class GatewayProxyIntegrationTest {
    @Test fun uploadObserveDownloadQuotaAuthThroughHttpsProxy() {
        val path = System.getenv("EDGEORE_GATEWAY_IT").orEmpty()
        assumeTrue("EDGEORE_GATEWAY_IT not set; proxy integration test skipped", path.isNotEmpty() && File(path).exists())
        val cfg = JSONObject(File(path).readText())
        val endpoint = cfg.getString("endpoint")
        val pin = cfg.getString("spkiPin")
        val token = cfg.getString("token")
        val quota = cfg.getLong("quotaBytes")

        val body = ByteArray(1024) { (it % 251).toByte() }
        val req = UploadRequest("it-job-1", body, Sha256.hex(body), body.size.toLong())

        // No pin and a wrong pin: refused in the TLS handshake.
        for (pins in listOf(
            emptyList(),
            listOf(
                "sha256/" +
                    java.util.Base64
                        .getEncoder()
                        .encodeToString(ByteArray(32)),
            ),
        )) {
            val e = runCatching { runBlocking { OwnedGatewayBackupProvider(endpoint, { token }, pins).upload(req) } }.exceptionOrNull()
            assertTrue("pins=$pins: expected SSLException, got $e", e is javax.net.ssl.SSLException)
        }

        // Wrong token: the proxy forwards, the gateway refuses with 401.
        val badAuth =
            runCatching {
                runBlocking { OwnedGatewayBackupProvider(endpoint, { "w".repeat(40) }, listOf(pin)).status("it-job-1") }
            }.exceptionOrNull()
        assertTrue("$badAuth", badAuth is BackupUnavailableException && badAuth.message!!.contains("401"))

        val p = OwnedGatewayBackupProvider(endpoint, { token }, listOf(pin))
        runBlocking {
            assertEquals(ProviderObservation.NotFound, p.status("it-job-1"))
            val ack = p.upload(req)
            assertTrue("$ack", ack is ProviderObservation.Acknowledged && ack.ciphertextSha256 == req.ciphertextSha256)
            assertTrue(p.upload(req) is ProviderObservation.Acknowledged) // idempotent
            assertTrue(p.status("it-job-1") is ProviderObservation.Acknowledged)
            val out = ByteArrayOutputStream()
            assertEquals(body.size.toLong(), p.download(req.ciphertextSha256, body.size.toLong(), out))
            assertArrayEquals(body, out.toByteArray())

            // Quota: fill to just past the configured limit; the gateway answers 409 and stores nothing.
            val big = ByteArray((quota - body.size + 1).toInt()) { 1 }
            val over = runCatching { p.upload(UploadRequest("it-job-2", big, Sha256.hex(big), big.size.toLong())) }.exceptionOrNull()
            assertTrue("$over", over is BackupUnavailableException && over.message!!.contains("409"))
            assertEquals(ProviderObservation.NotFound, p.status("it-job-2"))

            assertTrue(p.cancel("it-job-1"))
            assertTrue(p.status("it-job-1") is ProviderObservation.Canceled)
            val gone = runCatching { p.download(req.ciphertextSha256, body.size.toLong(), ByteArrayOutputStream()) }.exceptionOrNull()
            assertTrue("$gone", gone is BackupUnavailableException && gone.message!!.contains("404"))
        }
    }
}
