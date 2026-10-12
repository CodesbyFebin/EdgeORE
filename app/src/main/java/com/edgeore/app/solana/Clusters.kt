package com.edgeore.app.solana

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.BigInteger

/** The three Solana clusters, each with its RPC and expected genesis hash (checked before a balance is trusted). */
enum class Cluster(val id: String, val label: String, val rpc: String, val genesisHash: String) {
    DEVNET("devnet", "Devnet", "https://api.devnet.solana.com", "EtWTRABZaYq6iMfeYKouRu166VU2xqa1wcaWoxPkrZBG"),
    TESTNET("testnet", "Testnet", "https://api.testnet.solana.com", "4uhcVJyU9pJkvQyS88uRDiswHXSCkY3zQawwpjk2NsNY"),
    MAINNET("mainnet-beta", "Mainnet-beta", "https://api.mainnet-beta.solana.com", "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdpKuc147dw2N9d");

    companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } ?: DEVNET }
}

/** A token whose mint was verified on a cluster (docs/design/token-registry.md has the sources and the RPC evidence). */
data class TokenMint(val symbol: String, val label: String, val mint: String, val decimals: Int, val tokenProgram: String, val official: Boolean)

object TokenRegistry {
    const val SPL_TOKEN = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
    /** ORE: regolith-labs/ore api/src/consts.rs MINT_ADDRESS (@48c203bd); mainnet getAccountInfo: SPL Token mint, 11 decimals. */
    const val ORE_MINT = "oreoU2P8bN6jkk3jbaiVxYnG1dCXcYxwhwyK9jSybcp"
    /** SKR: docs.solanamobile.com/solana-mobile-stack/skr and solanamobile.com/blog/skr-is-live; mainnet: SPL Token mint, 6 decimals. */
    const val SKR_MINT = "SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3"

    /** Per cluster; a symbol missing here shows "Not on <cluster>". */
    fun mints(c: Cluster): List<TokenMint> = when (c) {
        Cluster.MAINNET -> listOf(TokenMint("ORE", "ORE", ORE_MINT, 11, SPL_TOKEN, true), TokenMint("SKR", "SKR", SKR_MINT, 6, SPL_TOKEN, true))
        // The ORE mint address exists on devnet with a different mint authority than mainnet: a test mint, not official ORE.
        Cluster.DEVNET -> listOf(TokenMint("ORE", "Test ORE", ORE_MINT, 11, SPL_TOKEN, false))
        Cluster.TESTNET -> emptyList()
    }
}

/** One balance read, tagged with where and when. [raw] is the integer amount; null [error] means the read succeeded. */
sealed interface BalanceRead {
    data object NotRead : BalanceRead
    data object Loading : BalanceRead
    data class NotOnCluster(val cluster: Cluster) : BalanceRead
    data class Value(val raw: BigInteger, val decimals: Int, val slot: Long, val cluster: Cluster, val account: String, val atMs: Long) : BalanceRead {
        fun formatted(): String = BigDecimal(raw, decimals).stripTrailingZeros().let { if (it.signum() == 0) "0" else it.toPlainString() }
    }
    data class Failed(val reason: String, val last: Value?) : BalanceRead
}

data class Balances(val cluster: Cluster = Cluster.DEVNET, val account: String? = null, val sol: BalanceRead = BalanceRead.NotRead,
                    val ore: BalanceRead = BalanceRead.NotRead, val skr: BalanceRead = BalanceRead.NotRead) {
    companion object { const val STALE_AFTER_MS = 120_000L }
}

/** Read-only balance RPC: getGenesisHash, getBalance, getTokenAccountsByOwner. No transaction methods. */
class BalanceRpc(private val cluster: Cluster, endpoint: String = cluster.rpc, timeoutMs: Int = 10_000) {
    private val http = JsonRpcHttp(endpoint, timeoutMs, 512 * 1024)

    fun genesisOk(): Boolean = http.call("getGenesisHash", JSONArray()).optString("result") == cluster.genesisHash

    fun sol(owner: String, nowMs: Long): BalanceRead.Value {
        val r = http.call("getBalance", JSONArray().put(owner).put(JSONObject().put("commitment", "confirmed"))).getJSONObject("result")
        return BalanceRead.Value(BigInteger.valueOf(r.getLong("value")), 9, r.getJSONObject("context").getLong("slot"), cluster, owner, nowMs)
    }

    /** Sums every token account the owner has for [mint]; decimals must match the registry, and accounts of another mint are refused. */
    fun token(owner: String, mint: TokenMint, nowMs: Long): BalanceRead.Value {
        val r = http.call("getTokenAccountsByOwner", JSONArray().put(owner).put(JSONObject().put("mint", mint.mint))
            .put(JSONObject().put("encoding", "jsonParsed").put("commitment", "confirmed"))).getJSONObject("result")
        return BalanceRead.Value(sumTokenAccounts(r.getJSONArray("value"), mint), mint.decimals, r.getJSONObject("context").getLong("slot"), cluster, owner, nowMs)
    }

    companion object {
        fun sumTokenAccounts(value: JSONArray, mint: TokenMint): BigInteger {
            var total = BigInteger.ZERO
            for (i in 0 until value.length()) {
                val acct = value.getJSONObject(i).getJSONObject("account")
                require(acct.getString("owner") == mint.tokenProgram) { "Token account not owned by the mint's token program" }
                val info = acct.getJSONObject("data").getJSONObject("parsed").getJSONObject("info")
                require(info.getString("mint") == mint.mint) { "Token account for another mint" }
                val amt = info.getJSONObject("tokenAmount")
                require(amt.getInt("decimals") == mint.decimals) { "Decimals differ from the verified mint" }
                total += BigInteger(amt.getString("amount"))
            }
            return total
        }
    }
}
