package com.edgeore.app.solana

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Base64

/** One account as returned by getMultipleAccounts: its owner program and raw data bytes. */
class RawAccount(val owner: String, val data: ByteArray)

/** Accounts read in one RPC call, with the slot the RPC reported for that read. */
class AccountsAt(val slot: Long, val accounts: List<RawAccount?>)

/** Slot duration measured by the RPC node (getRecentPerformanceSamples): numSeconds / numSlots of its latest sample. */
data class SlotRate(val msPerSlot: Double, val sampleSlots: Long, val sampleSeconds: Long)

/**
 * Read-only account access. Nothing behind this interface can build, sign or submit a transaction: it has only
 * getMultipleAccounts and getRecentPerformanceSamples. Used for observation (the ORE screen), never for wallet actions.
 */
interface AccountReader {
    val endpoint: String
    suspend fun accounts(addresses: List<String>): AccountsAt
    suspend fun slotRate(): SlotRate?
}

/**
 * Read-only RPC for observing accounts on any cluster the owner configures (the ORE screen uses a public mainnet RPC).
 * It deliberately has no transaction, blockhash or fee methods.
 */
class ReadOnlyAccountRpc(override val endpoint: String, timeoutMs: Int = 10_000) : AccountReader {
    private val http: JsonRpcHttp

    init {
        val u = runCatching { URI(endpoint) }.getOrNull()
        require(u != null && u.scheme == "https" && !u.host.isNullOrBlank() && u.userInfo == null) { "The RPC endpoint must be an https:// URL without credentials" }
        http = JsonRpcHttp(endpoint, timeoutMs, 256 * 1024)
    }

    override suspend fun accounts(addresses: List<String>): AccountsAt = withContext(Dispatchers.IO) {
        require(addresses.size in 1..10)
        addresses.forEach { com.edgeore.app.crypto.Base58.decodePublicKey(it) }
        val r = http.call("getMultipleAccounts", JSONArray().put(JSONArray(addresses))
            .put(JSONObject().put("encoding", "base64").put("commitment", "confirmed"))).getJSONObject("result")
        parseMultipleAccounts(r, addresses.size)
    }

    override suspend fun slotRate(): SlotRate? = withContext(Dispatchers.IO) {
        runCatching { parsePerformanceSample(http.call("getRecentPerformanceSamples", JSONArray().put(1))) }.getOrNull()
    }

    companion object {
        /** Exposed for tests. Throws on any malformed field rather than guessing. */
        fun parseMultipleAccounts(result: JSONObject, expected: Int): AccountsAt {
            val slot = result.getJSONObject("context").getLong("slot")
            val arr = result.getJSONArray("value")
            if (slot < 0 || arr.length() != expected) throw RpcException("Malformed getMultipleAccounts result")
            return AccountsAt(slot, (0 until arr.length()).map { i ->
                if (arr.isNull(i)) null else arr.getJSONObject(i).let { a ->
                    val d = a.getJSONArray("data")
                    if (d.getString(1) != "base64") throw RpcException("Unexpected account encoding")
                    RawAccount(a.getString("owner"), Base64.getDecoder().decode(d.getString(0)))
                }
            })
        }

        fun parsePerformanceSample(response: JSONObject): SlotRate? {
            val s = response.getJSONArray("result").optJSONObject(0) ?: return null
            val slots = s.getLong("numSlots"); val secs = s.getLong("samplePeriodSecs")
            if (slots <= 0 || secs <= 0) return null
            return SlotRate(secs * 1000.0 / slots, slots, secs)
        }
    }
}
