package com.edgeore.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * "Cloud fallback off" is enforced here: model endpoints must resolve to loopback or private
 * addresses (an owned host reached via adb reverse, SSH tunnel or LAN). Public hosts are refused.
 *
 * Every address the name resolves to must pass, and the connection is then made to the validated
 * address itself ([Decision.Allowed.boundUrl]), so a second DNS lookup cannot move the request
 * to a different host between check and use.
 */
object EndpointPolicy {
    sealed interface Decision {
        data class Allowed(val url: String, val location: String, val boundUrl: String = url, val originalHost: String = "") : Decision
        data class Refused(val reason: String) : Decision
    }

    fun check(endpoint: String, resolve: (String) -> InetAddress): Decision = checkAll(endpoint) { listOf(resolve(it)) }

    fun check(endpoint: String): Decision = checkAll(endpoint) { InetAddress.getAllByName(it).toList() }

    fun checkAll(endpoint: String, resolveAll: (String) -> List<InetAddress>): Decision {
        val uri = try { URI(endpoint.trim().trimEnd('/')) } catch (_: Exception) { return Decision.Refused("Not a valid URL") }
        if (uri.scheme != "http" && uri.scheme != "https") return Decision.Refused("Use http:// or https://")
        val host = uri.host ?: return Decision.Refused("URL needs a host")
        if (!uri.rawPath.isNullOrEmpty() || uri.rawQuery != null || uri.userInfo != null || uri.rawFragment != null) return Decision.Refused("Use scheme://host:port only")
        val addrs = try { resolveAll(host) } catch (_: Exception) { return Decision.Refused("Host does not resolve") }
        if (addrs.isEmpty()) return Decision.Refused("Host does not resolve")
        val verdicts = addrs.map { classify(it, uri.scheme) }
        verdicts.firstOrNull { it is Decision.Refused }?.let {
            return if (addrs.size > 1) Decision.Refused("${(it as Decision.Refused).reason} (one of ${addrs.size} resolved addresses)") else it
        }
        val chosen = addrs.first()
        val location = if (chosen.isLoopbackAddress) "Owned host via loopback tunnel ($host)" else "Owned host on private network ($host)"
        return Decision.Allowed(uri.toString(), location, boundUrl(uri, chosen), host)
    }

    private fun classify(addr: InetAddress, scheme: String): Decision = when {
        addr.isLoopbackAddress -> Decision.Allowed("", "")
        addr.isSiteLocalAddress || isCgnat(addr) || isUniqueLocalV6(addr) ->
            if (scheme == "http") Decision.Refused("Cleartext to LAN hosts is blocked; use a loopback tunnel (adb reverse / SSH) or https")
            else Decision.Allowed("", "")
        else -> Decision.Refused("Public endpoints are refused: cloud fallback is off")
    }

    /** URL that targets the validated IP literal, keeping scheme and port. */
    fun boundUrl(uri: URI, addr: InetAddress): String {
        val literal = if (addr is Inet6Address) "[" + addr.hostAddress!!.substringBefore('%') + "]" else addr.hostAddress!!
        val port = if (uri.port == -1) "" else ":${uri.port}"
        return "${uri.scheme}://$literal$port"
    }

    private fun isCgnat(a: InetAddress): Boolean {
        val b = a.address
        return b.size == 4 && (b[0].toInt() and 0xff) == 100 && (b[1].toInt() and 0xc0) == 64
    }
    private fun isUniqueLocalV6(a: InetAddress): Boolean { val b = a.address; return b.size == 16 && (b[0].toInt() and 0xfe) == 0xfc }
}

/**
 * Client for an Ollama-compatible model server running on hardware the user owns.
 * [cancelActive] closes the in-flight socket so a local cancel stops waiting immediately; it does
 * not prove the host stopped generating (Ollama's non-streaming API has no stop acknowledgement).
 */
class OwnedHostModelClient(
    private val baseUrl: String,
    private val timeoutMs: Int = 120_000,
    private val tlsHostname: String? = null,
) {
    constructor(decision: EndpointPolicy.Decision.Allowed, timeoutMs: Int = 120_000) :
        this(decision.boundUrl, timeoutMs, decision.originalHost.takeIf { decision.boundUrl.startsWith("https") && it.isNotEmpty() })

    @Volatile private var active: HttpURLConnection? = null
    @Volatile var cancelled: Boolean = false
        private set

    /** Closes the active request, if any. Returns true when a live connection was closed. */
    fun cancelActive(): Boolean {
        cancelled = true
        val c = active ?: return false
        runCatching { c.disconnect() }
        return true
    }

    suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val json = JSONObject(request("GET", "/api/tags", null))
        val arr = json.optJSONArray("models") ?: JSONArray()
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name")?.takeIf(String::isNotBlank) }
    }

    suspend fun chat(model: String, system: String, user: String): String = withContext(Dispatchers.IO) { chatBlocking(model, system, user) }

    fun chatBlocking(model: String, system: String, user: String): String {
        val body = buildChatRequest(model, system, user)
        val json = JSONObject(request("POST", "/api/chat", body))
        return json.optJSONObject("message")?.optString("content")?.takeIf { it.isNotEmpty() }
            ?: throw java.io.IOException("Model returned no message content")
    }

    companion object {
        fun buildChatRequest(model: String, system: String, user: String): String = JSONObject()
            .put("model", model)
            .put("stream", false)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user)))
            .toString()

        const val SYSTEM_PROMPT = "You are a private assistant running on hardware the user owns. Answer only from the provided document when one is given. You cannot approve transactions, grant permissions or change settings."
        const val MAX_DOCUMENT_CHARS = 24_000
        const val MAX_DOCUMENT_BYTES = 256L * 1024
        const val MAX_RESPONSE_BYTES = 2_000_000
    }

    private fun request(method: String, path: String, body: String?): String {
        if (cancelled) throw java.io.InterruptedIOException("Cancelled")
        val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
        active = conn
        try {
            if (conn is HttpsURLConnection && tlsHostname != null) {
                // Connected by validated IP; the certificate must still match the name the user entered.
                val default = HttpsURLConnection.getDefaultHostnameVerifier()
                conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, session -> default.verify(tlsHostname, session) }
            }
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = false
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            if (code in 300..399) throw java.io.IOException("Model host tried to redirect (HTTP $code); refused")
            if (code !in 200..299) throw java.io.IOException("Model host HTTP $code")
            val out = ByteArrayOutputStream()
            conn.inputStream.use { s ->
                val buf = ByteArray(8192)
                while (true) { val n = s.read(buf); if (n < 0) break; if (out.size() + n > MAX_RESPONSE_BYTES) throw java.io.IOException("Response too large"); out.write(buf, 0, n) }
            }
            return String(out.toByteArray(), Charsets.UTF_8)
        } catch (e: java.io.IOException) {
            if (cancelled) throw java.io.InterruptedIOException("Cancelled")
            throw e
        } finally {
            active = null
            conn.disconnect()
        }
    }
}
