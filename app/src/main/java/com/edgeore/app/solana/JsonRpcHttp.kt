package com.edgeore.app.solana

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Bounded JSON-RPC over HTTPS POST (no redirects). Shared by the wallet RPC client and [ReadOnlyAccountRpc]; it only moves JSON. */
internal class JsonRpcHttp(private val endpoint: String, private val timeoutMs: Int, private val maxResponse: Int) {
    fun call(method: String, params: JSONArray): JSONObject {
        val body = JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", method).put("params", params).toString()
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.instanceFollowRedirects = false
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code != 200) throw RpcException("RPC HTTP $code")
            val bytes = conn.inputStream.use { readBounded(it) }
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            if (json.has("error")) throw RpcException("RPC error: " + json.getJSONObject("error").optString("message", "unknown"))
            return json
        } finally {
            conn.disconnect()
        }
    }

    private fun readBounded(stream: java.io.InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = stream.read(buf)
            if (n < 0) break
            if (out.size() + n > maxResponse) throw RpcException("RPC response too large")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}

