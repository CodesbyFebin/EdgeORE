package com.edgeore.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL

/**
 * "Cloud fallback off" is enforced here: model endpoints must resolve to loopback or private
 * addresses (an owned host reached via adb reverse, SSH tunnel or LAN). Public hosts are refused.
 */
object EndpointPolicy {
    sealed interface Decision {
        data class Allowed(val url: String, val location: String) : Decision
        data class Refused(val reason: String) : Decision
    }

    fun check(endpoint: String, resolve: (String) -> InetAddress = { InetAddress.getByName(it) }): Decision {
        val uri = try { URI(endpoint.trim().trimEnd('/')) } catch (_: Exception) { return Decision.Refused("Not a valid URL") }
        if (uri.scheme != "http" && uri.scheme != "https") return Decision.Refused("Use http:// or https://")
        val host = uri.host ?: return Decision.Refused("URL needs a host")
        if (!uri.rawPath.isNullOrEmpty() || uri.rawQuery != null || uri.userInfo != null) return Decision.Refused("Use scheme://host:port only")
        val addr = try { resolve(host) } catch (_: Exception) { return Decision.Refused("Host does not resolve") }
        return when {
            addr.isLoopbackAddress -> Decision.Allowed(uri.toString(), "Owned host via loopback tunnel ($host)")
            addr.isSiteLocalAddress || isCgnat(addr) || isUniqueLocalV6(addr) ->
                if (uri.scheme == "http") Decision.Refused("Cleartext to LAN hosts is blocked; use a loopback tunnel (adb reverse / SSH) or https")
                else Decision.Allowed(uri.toString(), "Owned host on private network ($host)")
            else -> Decision.Refused("Public endpoints are refused: cloud fallback is off")
        }
    }

    private fun isCgnat(a: InetAddress): Boolean {
        val b = a.address
        return b.size == 4 && (b[0].toInt() and 0xff) == 100 && (b[1].toInt() and 0xc0) == 64
    }
    private fun isUniqueLocalV6(a: InetAddress): Boolean { val b = a.address; return b.size == 16 && (b[0].toInt() and 0xfe) == 0xfc }
}

/** Client for an Ollama-compatible model server running on hardware the user owns. */
class OwnedHostModelClient(private val baseUrl: String, private val timeoutMs: Int = 120_000) {

    suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val json = JSONObject(request("GET", "/api/tags", null))
        val arr = json.optJSONArray("models") ?: JSONArray()
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name")?.takeIf(String::isNotBlank) }
    }

    suspend fun chat(model: String, system: String, user: String): String = withContext(Dispatchers.IO) {
        val body = buildChatRequest(model, system, user)
        val json = JSONObject(request("POST", "/api/chat", body))
        json.optJSONObject("message")?.optString("content")?.takeIf { it.isNotEmpty() }
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
    }

    private fun request(method: String, path: String, body: String?): String {
        val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
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
            if (code !in 200..299) throw java.io.IOException("Model host HTTP $code")
            val out = ByteArrayOutputStream()
            conn.inputStream.use { s ->
                val buf = ByteArray(8192)
                while (true) { val n = s.read(buf); if (n < 0) break; if (out.size() + n > 2_000_000) throw java.io.IOException("Response too large"); out.write(buf, 0, n) }
            }
            return String(out.toByteArray(), Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }
}
