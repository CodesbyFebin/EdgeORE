package com.edgeore.app

import com.edgeore.app.solana.PortfolioRpc
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class WalletPortfolioTest {
    private val program = PortfolioRpc.TOKEN_PROGRAMS.first()
    private fun account(amount: String, owner: String = "wallet", mint: String = "mint", decimals: Int = 11): JSONObject = JSONObject()
        .put("account", JSONObject().put("owner", program).put("data", JSONObject().put("parsed", JSONObject().put("type", "account").put("info", JSONObject()
            .put("owner", owner).put("mint", mint).put("tokenAmount", JSONObject().put("amount", amount).put("decimals", decimals))))))
    @Test fun sumsEveryAccountWithoutFloatingPointOrSignedOverflow() {
        val a = JSONArray().put(account("18446744073709551615")).put(account("1"))
        assertEquals(BigInteger("18446744073709551616"), PortfolioRpc.sumAccounts(a, "wallet", "mint", program, 11))
        assertEquals("0.00000000001", PortfolioRpc.decimal(BigInteger.ONE, 11))
    }
    @Test fun emptySuccessfulReadIsZero() {
        assertEquals(BigInteger.ZERO, PortfolioRpc.sumAccounts(JSONArray(), "wallet", "mint", program, 11))
        assertEquals("0", PortfolioRpc.decimal(BigInteger.ZERO, 11))
    }
    @Test fun rejectsWrongOwnerMintDecimalsAndNegativeAmounts() {
        listOf(account("1", owner = "other"), account("1", mint = "other"), account("1", decimals = 9), account("-1"), account("18446744073709551616")).forEach {
            assertThrows(IllegalArgumentException::class.java) { PortfolioRpc.sumAccounts(JSONArray().put(it), "wallet", "mint", program, 11) }
        }
    }
    @Test fun failedTokenRefreshKeepsItsOwnTimestampAndNeverCrossesAccountsOrNetworks() {
        val old = com.edgeore.app.solana.WalletPortfolio("alice", assets = listOf(com.edgeore.app.solana.AssetBalance("ORE", "1", "Observed", 7, observedAt = 100)))
        val latest = com.edgeore.app.solana.WalletPortfolio("alice", assets = listOf(com.edgeore.app.solana.AssetBalance("ORE", status = "RPC unavailable")), observedAt = 200)
        val retained = latest.retainFailedAssets(old).assets.single()
        assertEquals("1", retained.amount); assertEquals(100L, retained.observedAt); assertTrue(retained.status.startsWith("Stale"))
        assertNull(latest.copy(account = "bob").retainFailedAssets(old).assets.single().amount)
        assertNull(latest.copy(network = com.edgeore.app.solana.ObservationNetwork.MAINNET).retainFailedAssets(old).assets.single().amount)
    }
}
