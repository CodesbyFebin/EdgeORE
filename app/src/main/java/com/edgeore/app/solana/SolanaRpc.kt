package com.edgeore.app.solana

import com.edgeore.app.crypto.Base58
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/** Read-only observations carry their source and freshness; failures are explicit, never zero. */
sealed interface RpcObservation {
    data class Fresh(val lamports: Long, val slot: Long, val observedAt: Long, val cluster: String) : RpcObservation
    data class Unavailable(val reason: String) : RpcObservation
}

class RpcException(message: String) : Exception(message)

/**
 * Minimal JSON-RPC client. Devnet only in this build: the app never targets mainnet.
 */
class SolanaRpc(private val endpoint: String = DEVNET, private val timeoutMs: Int = 10_000) : ChainGateway {
    companion object {
        const val DEVNET = "https://api.devnet.solana.com"
        const val CLUSTER = "devnet"
        private const val MAX_RESPONSE = 256 * 1024
    }

    suspend fun balance(address: String): RpcObservation = withContext(Dispatchers.IO) {
        try {
            Base58.decodePublicKey(address)
            val result = call("getBalance", JSONArray().put(address).put(JSONObject().put("commitment", "confirmed"))).getJSONObject("result")
            val amount = result.getLong("value")
            val slot = result.getJSONObject("context").getLong("slot")
            if (amount < 0 || slot < 0) throw RpcException("Malformed RPC result")
            RpcObservation.Fresh(amount, slot, System.currentTimeMillis(), CLUSTER)
        } catch (e: IllegalArgumentException) {
            RpcObservation.Unavailable("Invalid address")
        } catch (e: Exception) {
            RpcObservation.Unavailable(e.message ?: "RPC error or timeout")
        }
    }

    data class Blockhash(val blockhash: ByteArray, val lastValidBlockHeight: Long)

    suspend fun latestBlockhash(): Blockhash = withContext(Dispatchers.IO) {
        val value = call("getLatestBlockhash", JSONArray().put(JSONObject().put("commitment", "confirmed")))
            .getJSONObject("result").getJSONObject("value")
        val bh = Base58.decode(value.getString("blockhash"))
        if (bh.size != 32) throw RpcException("Malformed blockhash")
        Blockhash(bh, value.getLong("lastValidBlockHeight"))
    }

    /** Returns the fee in lamports, or null when the RPC cannot price the message (shown as uncertainty). */
    suspend fun feeForMessage(message: ByteArray): Long? = withContext(Dispatchers.IO) {
        try {
            val r = call("getFeeForMessage", JSONArray().put(Base64.getEncoder().encodeToString(message)).put(JSONObject().put("commitment", "confirmed")))
            val v = r.getJSONObject("result").opt("value")
            if (v is Number) v.toLong() else null
        } catch (_: Exception) { null }
    }

    /** Submits already signed, already verified bytes. Returns the RPC-reported signature. */
    suspend fun sendTransaction(signedTx: ByteArray): String = withContext(Dispatchers.IO) {
        val r = call("sendTransaction", JSONArray().put(Base64.getEncoder().encodeToString(signedTx))
            .put(JSONObject().put("encoding", "base64").put("preflightCommitment", "confirmed")))
        r.getString("result")
    }

    override suspend fun send(signedTx: ByteArray): String = sendTransaction(signedTx)

    override suspend fun status(signature: String): ChainStatus = withContext(Dispatchers.IO) {
        val r = call("getSignatureStatuses", JSONArray().put(JSONArray().put(signature)).put(JSONObject().put("searchTransactionHistory", true)))
        parseStatus(r)
    }

    /** Finalized block height, used only to decide whether a blockhash can still land. */
    override suspend fun blockHeight(): Long = withContext(Dispatchers.IO) {
        val h = call("getBlockHeight", JSONArray().put(JSONObject().put("commitment", "finalized"))).getLong("result")
        if (h < 0) throw RpcException("Malformed block height")
        h
    }

    private fun call(method: String, params: JSONArray): JSONObject {
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
            if (out.size() + n > MAX_RESPONSE) throw RpcException("RPC response too large")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

}

/**
 * Maps a getSignatureStatuses response for exactly one signature. Exposed for tests.
 * Only an explicit JSON null element is NOT_FOUND. A missing result/value, a value that is not a
 * one-element array, a non-object element, a missing `err`, or a missing/unknown confirmationStatus is
 * [ChainStatus.Unavailable]: the outcome stays uncertain and nothing is released.
 */
fun parseStatus(response: JSONObject): ChainStatus {
    val result = response.opt("result") as? JSONObject ?: return ChainStatus.Unavailable("Malformed status response: missing result")
    val value = result.opt("value") as? JSONArray ?: return ChainStatus.Unavailable("Malformed status response: missing value array")
    if (value.length() != 1) return ChainStatus.Unavailable("Malformed status response: expected 1 status, got ${value.length()}")
    val v = value.opt(0)
    if (v == JSONObject.NULL) return ChainStatus.NotFound
    if (v !is JSONObject) return ChainStatus.Unavailable("Malformed status response: status is ${v?.javaClass?.simpleName ?: "absent"}")
    if (!v.has("err")) return ChainStatus.Unavailable("Malformed status response: err field missing")
    if (!v.isNull("err")) return ChainStatus.Failed(v.get("err").toString())
    val cs = v.opt("confirmationStatus") as? String ?: return ChainStatus.Unavailable("Malformed status response: confirmationStatus missing")
    return when (cs) {
        "finalized" -> ChainStatus.Finalized
        "confirmed" -> ChainStatus.Confirmed
        "processed" -> ChainStatus.Processed
        else -> ChainStatus.Unavailable("Unknown confirmationStatus \"${cs.take(40)}\"")
    }
}
