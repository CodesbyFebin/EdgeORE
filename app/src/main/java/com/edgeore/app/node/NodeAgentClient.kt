package com.edgeore.app.node

import android.annotation.SuppressLint
import com.edgeore.app.crypto.Ed25519
import com.edgeore.app.crypto.Hex
import com.edgeore.app.crypto.Sha256
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.time.Instant
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.X509TrustManager

sealed class NodeResult<out T> {
    data class Ok<T>(val value: T, val operationId: String?, val payloadSha256: String?) : NodeResult<T>()
    data class Refused(val code: String, val message: String, val operationId: String?) : NodeResult<Nothing>()
    data class Unreachable(val message: String) : NodeResult<Nothing>()
}

/**
 * HTTPS client for the owner-run node agent. The node uses a self-signed certificate; trust is
 * established only by pinning the exact certificate SHA-256 the agent prints at start-up.
 * No arbitrary shell commands exist in this protocol.
 */
class NodeAgentClient(endpoint: String, certSha256Hex: String, private val timeoutMs: Int = 10_000) {
    val baseUrl: String
    private val pin: ByteArray

    init {
        val uri = try { URI(endpoint.trim().trimEnd('/')) } catch (_: Exception) { throw IllegalArgumentException("Endpoint is not a valid URL") }
        require(uri.scheme == "https") { "Endpoint must use https://" }
        require(!uri.host.isNullOrBlank()) { "Endpoint needs a host" }
        require(uri.rawPath.isNullOrEmpty() && uri.rawQuery == null && uri.userInfo == null) { "Endpoint must be scheme://host:port only" }
        baseUrl = uri.toString()
        val cleaned = certSha256Hex.trim().lowercase().replace(":", "")
        require(cleaned.length == 64) { "Certificate SHA-256 must be 64 hex characters" }
        pin = Hex.decode(cleaned)
    }

    // Deliberate: this is certificate pinning (exact leaf SHA-256), stricter than CA trust for a
    // self-signed, owner-run agent. It never accepts an unpinned certificate.
    @SuppressLint("CustomX509TrustManager")
    private val trustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            throw java.security.cert.CertificateException("Client certificates are not accepted")
        }
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull() ?: throw java.security.cert.CertificateException("No server certificate")
            if (!MessageDigest.isEqual(Sha256.digest(leaf.encoded), pin)) {
                throw java.security.cert.CertificateException("Certificate does not match the pinned SHA-256")
            }
            leaf.checkValidity()
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    /** The pinned leaf replaces name-based trust (agent certs name only 127.0.0.1/localhost). */
    private val pinVerifier = HostnameVerifier { _: String?, session: SSLSession ->
        val leaf = session.peerCertificates.firstOrNull() as? X509Certificate
        leaf != null && MessageDigest.isEqual(Sha256.digest(leaf.encoded), pin)
    }

    private val sslContext: SSLContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }

    fun pair(challenge: NodeAgentProtocol.Challenge, code: String, scopes: List<String>, key: Ed25519.KeyPair): NodeResult<String> {
        require(scopes.isNotEmpty() && scopes.all { it in NodeAgentProtocol.KNOWN_SCOPES }) { "Unsupported scope" }
        require(code.isNotBlank()) { "Pairing code required" }
        val pub = NodeAgentProtocol.b64(key.publicKey)
        val sig = key.sign(NodeAgentProtocol.pairPayload(challenge, pub, scopes).toByteArray(Charsets.UTF_8))
        val body = NodeAgentProtocol.pairRequest(challenge, code.trim(), pub, NodeAgentProtocol.b64(sig), scopes)
        return when (val r = post("/pair", body)) {
            is HttpOutcome.Ok -> {
                val id = r.json.optString("sessionId", "")
                if (id.isEmpty()) NodeResult.Refused("BAD_RESPONSE", "Node returned no session", null) else NodeResult.Ok(id, null, null)
            }
            is HttpOutcome.Error -> NodeResult.Refused(r.code, NodeAgentProtocol.explain(r.code), null)
            is HttpOutcome.Unreachable -> NodeResult.Unreachable(r.message)
        }
    }

    fun command(sessionId: String, key: Ed25519.KeyPair, action: String, now: Instant = Instant.now()): NodeResult<JSONObject> {
        val op = NodeAgentProtocol.newOperationId()
        val payload = NodeAgentProtocol.commandPayload(sessionId, op, now.plusSeconds(30), action).toByteArray(Charsets.UTF_8)
        val body = NodeAgentProtocol.signedEnvelope(payload, key.sign(payload))
        val digest = Sha256.hex(payload)
        return when (val r = post("/command", body)) {
            is HttpOutcome.Ok -> NodeResult.Ok(r.json, op, digest)
            is HttpOutcome.Error -> NodeResult.Refused(r.code, NodeAgentProtocol.explain(r.code), op)
            is HttpOutcome.Unreachable -> NodeResult.Unreachable(r.message)
        }
    }

    private sealed interface HttpOutcome {
        data class Ok(val json: JSONObject) : HttpOutcome
        data class Error(val code: String) : HttpOutcome
        data class Unreachable(val message: String) : HttpOutcome
    }

    private fun post(path: String, body: String): HttpOutcome {
        val conn = try { URL(baseUrl + path).openConnection() as HttpsURLConnection } catch (e: Exception) { return HttpOutcome.Unreachable("Invalid endpoint") }
        return try {
            conn.sslSocketFactory = sslContext.socketFactory
            conn.hostnameVerifier = pinVerifier
            conn.requestMethod = "POST"
            // An authenticated command has one intended destination; redirects are refused, not followed.
            conn.instanceFollowRedirects = false
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            if (status in 300..399) return HttpOutcome.Error("REDIRECT_REFUSED")
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { String(readBounded(it), Charsets.UTF_8) } ?: ""
            if (status in 200..299) {
                HttpOutcome.Ok(JSONObject(text))
            } else {
                val code = try { JSONObject(text).optString("error", "HTTP_$status") } catch (_: Exception) { text.trim().ifEmpty { "HTTP_$status" }.take(80) }
                HttpOutcome.Error(code)
            }
        } catch (e: javax.net.ssl.SSLException) {
            HttpOutcome.Unreachable("TLS failed: ${e.message ?: "handshake error"} (check the certificate SHA-256)")
        } catch (e: java.net.SocketTimeoutException) {
            HttpOutcome.Unreachable("Timed out after ${timeoutMs / 1000}s")
        } catch (e: java.io.IOException) {
            HttpOutcome.Unreachable("Network error: ${e.javaClass.simpleName}${e.message?.let { ": $it" } ?: ""}")
        } catch (e: org.json.JSONException) {
            HttpOutcome.Unreachable("Node returned non-JSON response")
        } finally {
            conn.disconnect()
        }
    }

    private fun readBounded(stream: java.io.InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (true) {
            val n = stream.read(buf)
            if (n < 0) break
            if (out.size() + n > 65536) throw java.io.IOException("Response too large")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
