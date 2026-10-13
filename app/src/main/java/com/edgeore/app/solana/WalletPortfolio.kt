package com.edgeore.app.solana

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.BigInteger

/** Observation network only. Signing and durable recovery remain pinned to devnet. */
enum class ObservationNetwork(
    val label: String,
    val endpoint: String,
    val genesis: String,
) {
    DEVNET("Devnet", "https://api.devnet.solana.com", "EtWTRABZaYq6iMfeYKouRu166VU2xqa1"),
    TESTNET("Testnet", "https://api.testnet.solana.com", "4uhcVJyU9pJkvQyS88uRDiswHXSCkY3zQawwpjk2NsNY"),
    MAINNET("Mainnet", "https://api.mainnet-beta.solana.com", "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp"),
}

data class AssetBalance(
    val symbol: String,
    val amount: String? = null,
    val status: String = "Not observed",
    val slot: Long? = null,
    val mint: String? = null,
    val observedAt: Long? = null,
)

data class WalletPortfolio(
    val account: String? = null,
    val network: ObservationNetwork = ObservationNetwork.DEVNET,
    val assets: List<AssetBalance> = listOf(AssetBalance("SOL"), AssetBalance("ORE")),
    val observedAt: Long? = null,
    val loading: Boolean = false,
    val error: String? = null,
) {
    fun retainFailedAssets(previous: WalletPortfolio?): WalletPortfolio {
        if (previous == null || previous.account != account || previous.network != network) return this
        return copy(
            assets =
                assets.map { fresh ->
                    val old = previous.assets.find { it.symbol == fresh.symbol }
                    if (fresh.amount == null &&
                        fresh.status == "RPC unavailable" &&
                        old?.amount != null
                    ) {
                        old.copy(status = "Stale · RPC unavailable")
                    } else {
                        fresh
                    }
                },
        )
    }
}

/** No submission methods. Validate genesis and mint owner/decimals before interpreting token accounts. */
class PortfolioRpc(
    private val network: ObservationNetwork,
) {
    private val http = JsonRpcHttp(network.endpoint, 10_000, 2 * 1024 * 1024)

    suspend fun read(account: String): WalletPortfolio =
        withContext(Dispatchers.IO) {
            com.edgeore.app.crypto.Base58
                .decodePublicKey(account)
            check(http.call("getGenesisHash", JSONArray()).getString("result") == network.genesis) { "NETWORK_MISMATCH" }
            val sol =
                http
                    .call(
                        "getBalance",
                        JSONArray().put(account).put(JSONObject().put("commitment", "confirmed")),
                    ).getJSONObject("result")
            val lamports = BigInteger(sol.get("value").toString())
            require(lamports.signum() >= 0)
            val solAsset =
                AssetBalance(
                    "SOL",
                    decimal(lamports, 9),
                    "Observed · confirmed",
                    sol.getJSONObject("context").getLong("slot").also {
                        require(
                            it >= 0,
                        )
                    },
                    observedAt = System.currentTimeMillis(),
                )
            val ore =
                if (network == ObservationNetwork.MAINNET) {
                    runCatching { token(account, ORE_MINT, "ORE", 11) }
                        .getOrElse { AssetBalance("ORE", status = "RPC unavailable", mint = ORE_MINT) }
                } else {
                    AssetBalance("ORE", status = "Not available on this network")
                }
            WalletPortfolio(
                account,
                network,
                listOf(solAsset, ore),
                System.currentTimeMillis(),
            )
        }

    private fun token(
        account: String,
        mint: String,
        symbol: String,
        expectedDecimals: Int,
    ): AssetBalance {
        val info =
            http
                .call(
                    "getAccountInfo",
                    JSONArray().put(mint).put(JSONObject().put("encoding", "jsonParsed").put("commitment", "confirmed")),
                ).getJSONObject("result")
                .getJSONObject("value")
        val owner = info.getString("owner")
        require(owner in TOKEN_PROGRAMS) { "Mint owner not supported" }
        val parsed = info.getJSONObject("data").getJSONObject("parsed")
        require(parsed.getString("type") == "mint")
        val decimals = parsed.getJSONObject("info").getInt("decimals")
        require(decimals == expectedDecimals)
        val response =
            http
                .call(
                    "getTokenAccountsByOwner",
                    JSONArray()
                        .put(
                            account,
                        ).put(JSONObject().put("mint", mint))
                        .put(JSONObject().put("encoding", "jsonParsed").put("commitment", "confirmed")),
                ).getJSONObject("result")
        return AssetBalance(
            symbol,
            decimal(sumAccounts(response.getJSONArray("value"), account, mint, owner, decimals), decimals),
            "Observed · confirmed",
            response.getJSONObject("context").getLong("slot").also {
                require(
                    it >= 0,
                )
            },
            mint,
            System.currentTimeMillis(),
        )
    }

    companion object {
        // regolith-labs/ore api/src/consts.rs at 48c203bd75db3cc45105ec29d8f8db719e5a2263.
        const val ORE_MINT = "oreoU2P8bN6jkk3jbaiVxYnG1dCXcYxwhwyK9jSybcp"
        val TOKEN_PROGRAMS = setOf("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA", "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb")

        fun decimal(
            amount: BigInteger,
            decimals: Int,
        ): String {
            require(amount.signum() >= 0 && decimals in 0..18)
            return BigDecimal(amount, decimals).stripTrailingZeros().toPlainString()
        }

        fun sumAccounts(
            values: JSONArray,
            account: String,
            mint: String,
            program: String,
            decimals: Int,
        ): BigInteger {
            var total = BigInteger.ZERO
            for (i in 0 until values.length()) {
                val a = values.getJSONObject(i).getJSONObject("account")
                require(a.getString("owner") == program)
                val parsed = a.getJSONObject("data").getJSONObject("parsed")
                require(parsed.getString("type") == "account")
                val info = parsed.getJSONObject("info")
                require(info.getString("owner") == account && info.getString("mint") == mint)
                val token = info.getJSONObject("tokenAmount")
                require(token.getInt("decimals") == decimals)
                val raw = token.getString("amount")
                require(raw.matches(Regex("[0-9]{1,20}")))
                val n = BigInteger(raw)
                require(n <= BigInteger("18446744073709551615"))
                total += n
            }
            return total
        }
    }
}
