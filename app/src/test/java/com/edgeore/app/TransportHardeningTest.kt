package com.edgeore.app

import com.edgeore.app.ai.EndpointPolicy
import com.edgeore.app.ai.OwnedHostModelClient
import com.edgeore.app.crypto.Ed25519
import com.edgeore.app.crypto.Hex
import com.edgeore.app.crypto.Sha256
import com.edgeore.app.device.CpuRateTracker
import com.edgeore.app.device.CpuSample
import com.edgeore.app.device.DiskRateTracker
import com.edgeore.app.device.DiskSample
import com.edgeore.app.device.ioBytesPerSecond
import com.edgeore.app.node.NodeAgentClient
import com.edgeore.app.node.NodeResult
import com.edgeore.app.solana.ChainStatus
import com.edgeore.app.solana.parseStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.security.KeyStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import kotlin.concurrent.thread

/** P1: AI transport cancellation, bound endpoint resolution, node redirect refusal, telemetry continuity. */
class TransportHardeningTest {
    private fun v4(s: String) = InetAddress.getByAddress(s, s.split('.').map { it.toInt().toByte() }.toByteArray())

    // ---------- endpoint binding ----------
    @Test fun everyResolvedAddressMustPass() {
        val mixed = EndpointPolicy.checkAll("https://models.home") { listOf(v4("192.168.1.9"), v4("93.184.216.34")) }
        assertTrue(mixed is EndpointPolicy.Decision.Refused)
        assertTrue((mixed as EndpointPolicy.Decision.Refused).reason.contains("one of 2"))
        val ok = EndpointPolicy.checkAll("https://models.home:8443") { listOf(v4("192.168.1.9"), v4("10.0.0.4")) }
        assertTrue(ok is EndpointPolicy.Decision.Allowed)
    }

    @Test fun connectionTargetsTheValidatedAddressNotASecondLookup() {
        val d = EndpointPolicy.checkAll("https://models.home:8443") { listOf(v4("192.168.1.9")) } as EndpointPolicy.Decision.Allowed
        assertEquals("https://192.168.1.9:8443", d.boundUrl)
        assertEquals("models.home", d.originalHost)
        val loop = EndpointPolicy.checkAll("http://localhost:11434") { listOf(InetAddress.getByAddress("localhost", ByteArray(16).also { it[15] = 1 })) } as EndpointPolicy.Decision.Allowed
        assertEquals("http://[0:0:0:0:0:0:0:1]:11434", loop.boundUrl)
        assertEquals("http://127.0.0.1", EndpointPolicy.boundUrl(URI("http://127.0.0.1"), v4("127.0.0.1")))
    }

    @Test fun fragmentsAndUnresolvableHostsRefused() {
        assertTrue(EndpointPolicy.checkAll("http://127.0.0.1:1#x") { listOf(v4("127.0.0.1")) } is EndpointPolicy.Decision.Refused)
        assertTrue(EndpointPolicy.checkAll("http://nowhere") { emptyList() } is EndpointPolicy.Decision.Refused)
        assertTrue(EndpointPolicy.checkAll("http://nowhere") { throw java.net.UnknownHostException() } is EndpointPolicy.Decision.Refused)
    }

    // ---------- AI cancellation ----------
    @Test fun localCancelClosesTheSocketPromptly() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val accepted = CountDownLatch(1)
            thread(isDaemon = true) { runCatching { server.accept().also { accepted.countDown(); Thread.sleep(30_000) } } }
            val client = OwnedHostModelClient("http://127.0.0.1:${server.localPort}", timeoutMs = 60_000)
            var error: Throwable? = null
            val done = CountDownLatch(1)
            val t = thread { try { client.chatBlocking("m", "s", "u") } catch (e: Throwable) { error = e } finally { done.countDown() } }
            assertTrue(accepted.await(5, TimeUnit.SECONDS))
            Thread.sleep(200)
            val started = System.nanoTime()
            assertTrue("a live connection was closed", client.cancelActive())
            assertTrue("request still waiting after cancel", done.await(3, TimeUnit.SECONDS))
            val ms = (System.nanoTime() - started) / 1_000_000
            assertTrue("took $ms ms", ms < 3000)
            assertTrue(error.toString(), error is java.io.InterruptedIOException)
            t.join(1000)
        }
    }

    @Test fun cancelledClientRefusesNewRequests() {
        val c = OwnedHostModelClient("http://127.0.0.1:9")
        assertTrue(!c.cancelActive())
        assertTrue(runCatching { c.chatBlocking("m", "s", "u") }.exceptionOrNull() is java.io.InterruptedIOException)
    }

    @Test fun modelHostRedirectIsRefused() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            thread(isDaemon = true) {
                runCatching { server.accept().use { s ->
                    s.getInputStream().read(ByteArray(4096))
                    s.getOutputStream().write("HTTP/1.1 302 Found\r\nLocation: https://example.com/api/tags\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                } }
            }
            val e = runCatching { kotlinx.coroutines.runBlocking { OwnedHostModelClient("http://127.0.0.1:${server.localPort}").listModels() } }.exceptionOrNull()
            assertTrue(e.toString(), e?.message?.contains("redirect") == true)
        }
    }

    // ---------- node redirect refusal over real pinned TLS ----------
    private fun selfSignedKeystore(): Pair<KeyStore, String> {
        val dir = createTempDir()
        val ks = File(dir, "node.p12")
        val keytool = File(System.getProperty("java.home"), "bin/keytool").absolutePath
        val p = ProcessBuilder(keytool, "-genkeypair", "-alias", "node", "-keyalg", "EC", "-groupname", "secp256r1", "-dname", "CN=127.0.0.1",
            "-ext", "SAN=ip:127.0.0.1", "-validity", "2", "-storetype", "PKCS12", "-keystore", ks.absolutePath, "-storepass", "changeit", "-keypass", "changeit")
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        check(p.waitFor() == 0) { "keytool failed: $out" }
        val store = KeyStore.getInstance("PKCS12").apply { ks.inputStream().use { load(it, "changeit".toCharArray()) } }
        val pin = Hex.encode(Sha256.digest(store.getCertificate("node").encoded))
        dir.deleteRecursively()
        return store to pin
    }

    private fun tlsServer(store: KeyStore, response: String): SSLServerSocket {
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, "changeit".toCharArray()) }
        val ctx = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) }
        val server = ctx.serverSocketFactory.createServerSocket(0, 1, InetAddress.getLoopbackAddress()) as SSLServerSocket
        thread(isDaemon = true) {
            runCatching { server.accept().use { s ->
                val buf = ByteArray(8192); s.getInputStream().read(buf)
                s.getOutputStream().write(response.toByteArray()); s.getOutputStream().flush()
            } }
        }
        return server
    }

    @Test fun nodeRedirectIsRefusedNotFollowed() {
        val (store, pin) = selfSignedKeystore()
        tlsServer(store, "HTTP/1.1 307 Temporary Redirect\r\nLocation: https://127.0.0.1:1/command\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").use { server ->
            val client = NodeAgentClient("https://127.0.0.1:${server.localPort}", pin, timeoutMs = 5_000)
            val r = client.command("session", Ed25519.generate(), "observe")
            assertTrue(r.toString(), r is NodeResult.Refused && r.code == "REDIRECT_REFUSED")
        }
    }

    @Test fun pinnedTlsStillWorksForANormalAnswer() {
        val (store, pin) = selfSignedKeystore()
        val body = "{\"fingerprint\":\"abc\",\"hostOS\":\"linux\"}"
        tlsServer(store, "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body").use { server ->
            val r = NodeAgentClient("https://127.0.0.1:${server.localPort}", pin, timeoutMs = 5_000).command("session", Ed25519.generate(), "observe")
            assertTrue(r.toString(), r is NodeResult.Ok && r.value.getString("hostOS") == "linux")
        }
    }

    @Test fun wrongPinIsUnreachableNotTrusted() {
        val (store, _) = selfSignedKeystore()
        tlsServer(store, "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\n{}").use { server ->
            val r = NodeAgentClient("https://127.0.0.1:${server.localPort}", "00".repeat(32), timeoutMs = 5_000).command("s", Ed25519.generate(), "observe")
            assertTrue(r.toString(), r is NodeResult.Unreachable)
        }
    }

    // ---------- telemetry continuity ----------
    @Test fun missingDiskSampleDoesNotInflateTheRate() {
        val t = DiskRateTracker()
        assertNull(t.observe(DiskSample(1_000_000), 0))
        assertEquals(1000L, t.observe(DiskSample(1_001_000), 1_000))
        assertNull("missing sample is unavailable", t.observe(null, 2_000))
        // The old reader would compute (1_061_000 - 1_001_000) over 1 s = 60 KB/s here.
        assertNull("continuity was lost: no rate across the gap", t.observe(DiskSample(1_061_000), 3_000))
        assertEquals(2000L, t.observe(DiskSample(1_063_000), 4_000))
    }

    @Test fun counterResetAndClockStallAreUnavailableNotZero() {
        val t = DiskRateTracker()
        t.observe(DiskSample(5_000), 0)
        assertNull(t.observe(DiskSample(100), 1_000))
        assertNull(t.observe(DiskSample(200), 1_000))
        assertNull(ioBytesPerSecond(DiskSample(10), DiskSample(5), 1000))
        val c = CpuRateTracker()
        assertNull(c.observe(CpuSample(10, 100)))
        assertNull(c.observe(null))
        assertNull(c.observe(CpuSample(20, 200)))
        assertEquals(50, c.observe(CpuSample(70, 300)))
    }

    // ---------- RPC status mapping ----------
    @Test fun signatureStatusMapping() {
        fun r(v: String) = JSONObject("{\"result\":{\"value\":[$v]}}")
        assertEquals(ChainStatus.NotFound, parseStatus(r("null")))
        assertEquals(ChainStatus.Finalized, parseStatus(r("{\"err\":null,\"confirmationStatus\":\"finalized\"}")))
        assertEquals(ChainStatus.Confirmed, parseStatus(r("{\"err\":null,\"confirmationStatus\":\"confirmed\"}")))
        assertEquals(ChainStatus.Processed, parseStatus(r("{\"err\":null,\"confirmationStatus\":\"processed\"}")))
        assertTrue(parseStatus(r("{\"err\":{\"InstructionError\":[0,1]},\"confirmationStatus\":\"finalized\"}")) is ChainStatus.Failed)
    }

    @Test fun malformedStatusIsUnavailableNeverNotFound() {
        fun r(v: String) = JSONObject("{\"result\":{\"value\":[$v]}}")
        fun un(o: JSONObject) = assertTrue("expected Unavailable for $o", parseStatus(o) is ChainStatus.Unavailable)
        un(JSONObject("{\"result\":{\"value\":[]}}"))            // empty array
        un(JSONObject("{\"result\":{\"value\":[null,null]}}"))   // wrong length
        un(r("\"finalized\"")); un(r("42")); un(r("true")); un(r("[]")) // wrong element type
        un(JSONObject("{}"))                                          // missing result
        un(JSONObject("{\"result\":{}}"))                           // missing value
        un(JSONObject("{\"result\":{\"value\":null}}"))           // value not an array
        un(JSONObject("{\"result\":{\"value\":{}}}"))
        un(JSONObject("{\"result\":\"x\"}"))
        un(r("{\"confirmationStatus\":\"finalized\"}"))             // err missing
        un(r("{\"err\":null}"))                                        // status missing
        un(r("{\"err\":null,\"confirmationStatus\":\"rooted\"}"))   // unknown status
        un(r("{\"err\":null,\"confirmationStatus\":7}"))
        // Only an explicit, well-formed null is NOT_FOUND.
        assertEquals(ChainStatus.NotFound, parseStatus(r("null")))
    }
}
