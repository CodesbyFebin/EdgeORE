package com.edgeore.app

import com.edgeore.app.crypto.Sha256
import com.edgeore.app.node.NodeAgentClient
import com.edgeore.app.storage.SpkiPinning
import org.junit.AfterClass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** Hardening 8: node agent accepts an SPKI pin as well as the exact-certificate pin; RPC trusts system CAs only. */
class NodeAgentSpkiPinTest {
    companion object {
        private lateinit var server: TlsTestServer
        @BeforeClass @JvmStatic fun start() { server = TlsTestServer { _, _, _ -> 200 to "{}" } }
        @AfterClass @JvmStatic fun stop() { server.close() }
    }

    @Test fun spkiPinAndCertificatePinBothMatchAndOthersDoNot() {
        val cert = server.certificate
        assertTrue(NodeAgentClient(server.url, SpkiPinning.format(cert)).matches(cert))
        assertTrue(NodeAgentClient(server.url, Sha256.hex(cert.encoded)).matches(cert))
        val other = "sha256/" + java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 7 })
        assertFalse(NodeAgentClient(server.url, other).matches(cert))
        // An SPKI hash given in the hex form is treated as a certificate hash and does not match.
        assertFalse(NodeAgentClient(server.url, Sha256.hex(cert.publicKey.encoded)).matches(cert))
    }

    @Test fun malformedPinIsRejected() {
        assertTrue(runCatching { NodeAgentClient(server.url, "sha256/short") }.isFailure)
        assertTrue(runCatching { NodeAgentClient(server.url, "abc") }.isFailure)
    }

    @Test fun networkConfigTrustsSystemCasOnly() {
        val xml = File("src/main/res/xml/network_security_config.xml").readText()
        assertTrue(xml.contains("<certificates src=\"system\" />"))
        assertFalse(xml.contains("src=\"user\""))
        assertFalse(xml.contains("debug-overrides"))
    }
}
