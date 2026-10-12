package com.edgeore.app

import com.edgeore.app.crypto.Sha256
import com.edgeore.app.storage.OwnedGatewayBackupProvider
import com.edgeore.app.storage.ProviderObservation
import com.edgeore.app.storage.SpkiPinning
import com.edgeore.app.storage.UploadRequest
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

/** SPKI pinning for the owned gateway client, against a local self-signed TLS server. */
class SpkiPinningTest {
    companion object {
        private val body = "opaque ciphertext fixture".toByteArray()
        private val digest = Sha256.hex(body)
        private val ack = """{"state":"acknowledged","remoteRef":"$digest","ciphertextSha256":"$digest","observedAt":"2026-10-11T00:00:00Z"}"""
        private lateinit var server: TlsTestServer

        @BeforeClass @JvmStatic fun start() { server = TlsTestServer { _, _, _ -> 200 to ack } }
        @AfterClass @JvmStatic fun stop() { server.close() }
    }

    private val token = { "t".repeat(40) }
    private val otherPin = "sha256/" + java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 7 })
    private fun provider(pins: List<String>) = OwnedGatewayBackupProvider(server.url, token, pins)

    @Before fun reset() { server.requests.set(0); server.bodyBytes.set(0) }

    @Test fun correctPinConnectsAndUploads() {
        val obs = runBlocking { provider(listOf(SpkiPinning.format(server.certificate))).upload(UploadRequest("job-1", body, digest, body.size.toLong())) }
        assertTrue(obs is ProviderObservation.Acknowledged)
        assertEquals(1, server.requests.get())
        assertEquals(body.size.toLong(), server.bodyBytes.get())
    }

    @Test fun hexPinFormIsEquivalent() {
        val hex = Sha256.hex(server.certificate.publicKey.encoded)
        val obs = runBlocking { provider(listOf(otherPin, hex)).status("job-1") }
        assertTrue(obs is ProviderObservation.Acknowledged)
    }

    @Test fun wrongPinFailsBeforeAnyRequestOrBodyIsSent() {
        val e = runCatching { runBlocking { provider(listOf(otherPin)).upload(UploadRequest("job-1", body, digest, body.size.toLong())) } }.exceptionOrNull()
        assertTrue("expected a TLS failure, got $e", e is javax.net.ssl.SSLException)
        assertEquals(0, server.requests.get())
        assertEquals(0L, server.bodyBytes.get())
    }

    @Test fun noPinRefusesSelfSignedHostBeforeAnyRequest() {
        val e = runCatching { runBlocking { provider(emptyList()).upload(UploadRequest("job-1", body, digest, body.size.toLong())) } }.exceptionOrNull()
        assertTrue("expected a TLS failure, got $e", e is javax.net.ssl.SSLException)
        assertEquals(0, server.requests.get())
        assertEquals(0L, server.bodyBytes.get())
    }

    @Test fun malformedPinsAreRefusedAtSetup() {
        for (bad in listOf("sha256/", "sha256/AAAA", "abc", "g".repeat(64), "sha1/" + "A".repeat(28))) {
            val e = runCatching { OwnedGatewayBackupProvider("https://example.com", token, listOf(bad)) }.exceptionOrNull()
            assertTrue("$bad should be refused, got $e", e is IllegalArgumentException)
        }
    }

    @Test fun pinIsSpkiNotWholeCertificate() {
        val certHash = Sha256.hex(server.certificate.encoded)
        val e = runCatching { runBlocking { provider(listOf(certHash)).status("job-1") } }.exceptionOrNull()
        assertTrue("a whole-certificate hash must not satisfy an SPKI pin, got $e", e is javax.net.ssl.SSLException)
        assertEquals(0, server.requests.get())
    }
}
