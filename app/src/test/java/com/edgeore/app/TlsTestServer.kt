package com.edgeore.app

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.InetAddress
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread

/**
 * A minimal local HTTPS/1.1 server for JVM tests. Its certificate is self-signed for 127.0.0.1 and generated at test
 * time with the JDK's keytool into a temporary directory (no keystore is committed).
 *
 * [requests] counts requests whose request line was read; [bodyBytes] counts request-body bytes read. A handshake that
 * the client refuses leaves both at zero, which is how the tests show nothing was sent.
 */
class TlsTestServer(private val respond: (method: String, path: String, body: ByteArray) -> Pair<Int, String>) : AutoCloseable {
    val certificate: X509Certificate
    val requests = AtomicInteger()
    val bodyBytes = AtomicLong()
    val handshakeFailures = AtomicInteger()
    private val server: SSLServerSocket
    private val dir: File = kotlin.io.path.createTempDirectory("edgeore-tls").toFile()

    init {
        val password = ByteArray(18).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val store = File(dir, "server.p12")
        val keytool = File(System.getProperty("java.home"), "bin/keytool").path
        val p = ProcessBuilder(
            keytool, "-genkeypair", "-alias", "server", "-keyalg", "EC", "-groupname", "secp256r1",
            "-dname", "CN=edgeore-test", "-ext", "san=ip:127.0.0.1", "-validity", "2",
            "-storetype", "PKCS12", "-keystore", store.path, "-storepass", password, "-keypass", password,
        ).redirectErrorStream(true).start()
        val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
        check(p.waitFor() == 0) { "keytool failed: $out" }
        val ks = KeyStore.getInstance("PKCS12").apply { store.inputStream().use { load(it, password.toCharArray()) } }
        certificate = ks.getCertificate("server") as X509Certificate
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, password.toCharArray()) }
        val ctx = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) }
        server = ctx.serverSocketFactory.createServerSocket(0, 16, InetAddress.getLoopbackAddress()) as SSLServerSocket
        thread(isDaemon = true, name = "tls-test-server") { acceptLoop() }
    }

    val url: String get() = "https://127.0.0.1:${server.localPort}"

    private fun acceptLoop() {
        while (!server.isClosed) {
            val s = runCatching { server.accept() as SSLSocket }.getOrNull() ?: break
            thread(isDaemon = true) { s.use { serve(it) } }
        }
    }

    private fun readLine(input: InputStream): String? {
        val b = ByteArrayOutputStream()
        while (true) {
            val c = input.read()
            if (c < 0) return if (b.size() == 0) null else b.toString(Charsets.ISO_8859_1.name())
            if (c == '\n'.code) return b.toString(Charsets.ISO_8859_1.name()).trimEnd('\r')
            b.write(c)
        }
    }

    private fun serve(s: SSLSocket) {
        s.soTimeout = 5_000
        try { s.startHandshake() } catch (_: Exception) { handshakeFailures.incrementAndGet(); return }
        val input = s.inputStream
        val requestLine = readLine(input) ?: return
        requests.incrementAndGet()
        var length = 0
        while (true) {
            val h = readLine(input) ?: return
            if (h.isEmpty()) break
            val (k, v) = h.split(":", limit = 2).let { it[0].trim().lowercase() to it.getOrElse(1) { "" }.trim() }
            if (k == "content-length") length = v.toInt()
        }
        val body = input.readNBytes(length)
        bodyBytes.addAndGet(body.size.toLong())
        val (method, path) = requestLine.split(" ").let { it[0] to it[1] }
        val (code, json) = respond(method, path, body)
        val bytes = json.toByteArray(Charsets.UTF_8)
        s.outputStream.apply {
            write("HTTP/1.1 $code X\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
            write(bytes); flush()
        }
    }

    override fun close() {
        server.close()
        dir.deleteRecursively()
    }
}
