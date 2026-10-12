package com.edgeore.app

import com.edgeore.app.bot.OreBot
import com.edgeore.app.device.CheckInState
import com.edgeore.app.device.DailyCheckIn
import com.edgeore.app.device.NetActivity
import com.edgeore.app.device.Telemetry
import com.edgeore.app.solana.BalanceRead
import com.edgeore.app.solana.BalanceRpc
import com.edgeore.app.solana.Cluster
import com.edgeore.app.solana.TokenRegistry
import com.edgeore.app.ui.screens.balanceText
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigInteger
import java.time.Instant

class MintHoloLogicTest {
    private val t0 = Instant.parse("2026-10-12T08:00:00Z").toEpochMilli()
    private val h = DailyCheckIn.SLOT_MS

    @Test fun fiveOneHourSlotsPerUtcDayAndAStreak() {
        var s = CheckInState()
        for (i in 0 until 5) {
            val r = DailyCheckIn.checkIn(s, t0 + i * h) as DailyCheckIn.Result.Started; s = r.state
            assertTrue(DailyCheckIn.checkIn(s, t0 + i * h + 10_000) is DailyCheckIn.Result.Refused) // slot still active
        }
        assertEquals(5, s.points); assertEquals(1, s.streakDays)
        assertEquals("All 5 slots used today", (DailyCheckIn.checkIn(s, t0 + 5 * h + 1) as DailyCheckIn.Result.Refused).reason)
        val next = (DailyCheckIn.checkIn(s, t0 + 24 * h) as DailyCheckIn.Result.Started).state
        assertEquals(2, next.streakDays); assertEquals(1, DailyCheckIn.used(next, t0 + 24 * h))
        val gap = (DailyCheckIn.checkIn(next, t0 + 72 * h) as DailyCheckIn.Result.Started).state
        assertEquals(1, gap.streakDays) // missing a day starts a new streak; nothing else is taken away
        assertEquals(gap, CheckInState.fromJson(gap.toJson()))
        assertEquals("Streak and points saved on your phone", DailyCheckIn.REWARD_LABEL) // local only, no token claimed
    }

    @Test fun tokenAmountsAreSummedOnlyForTheVerifiedMint() {
        val ore = TokenRegistry.mints(Cluster.MAINNET).first { it.symbol == "ORE" }
        fun acct(mint: String, amount: String, dec: Int = 11, owner: String = TokenRegistry.SPL_TOKEN) = JSONObject().put("account", JSONObject().put("owner", owner)
            .put("data", JSONObject().put("parsed", JSONObject().put("info", JSONObject().put("mint", mint).put("tokenAmount", JSONObject().put("amount", amount).put("decimals", dec))))))
        assertEquals(BigInteger("300000000000000000001"), BalanceRpc.sumTokenAccounts(JSONArray().put(acct(ore.mint, "300000000000000000000")).put(acct(ore.mint, "1")), ore))
        assertEquals(BigInteger.ZERO, BalanceRpc.sumTokenAccounts(JSONArray(), ore))
        for (bad in listOf(acct(TokenRegistry.SKR_MINT, "1"), acct(ore.mint, "1", dec = 6), acct(ore.mint, "1", owner = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb")))
            assertTrue(runCatching { BalanceRpc.sumTokenAccounts(JSONArray().put(bad), ore) }.isFailure)
    }

    @Test fun registryMatchesTheVerifiedMints() {
        assertEquals("oreoU2P8bN6jkk3jbaiVxYnG1dCXcYxwhwyK9jSybcp", TokenRegistry.ORE_MINT)
        assertEquals("SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3", TokenRegistry.SKR_MINT)
        assertTrue(TokenRegistry.mints(Cluster.TESTNET).isEmpty())
        assertFalse(TokenRegistry.mints(Cluster.DEVNET).first().official)
        assertEquals(Cluster.DEVNET, Cluster.of(null)); assertEquals(Cluster.MAINNET, Cluster.of("mainnet-beta"))
    }

    @Test fun balanceStatesAreHonest() {
        val v = BalanceRead.Value(BigInteger.ZERO, 9, 5, Cluster.DEVNET, "x", 0)
        assertEquals("0", balanceText(v, 1_000, true).first)
        assertTrue(balanceText(v, 1_000_000, true).second.startsWith("Stale"))
        assertEquals("Unavailable", balanceText(BalanceRead.Failed("RPC unavailable", null), 0, true).first)
        assertEquals("Not on Testnet", balanceText(BalanceRead.NotOnCluster(Cluster.TESTNET), 0, true).first)
        assertEquals("—", balanceText(BalanceRead.NotRead, 0, false).first)
        assertEquals("1.5", BalanceRead.Value(BigInteger.valueOf(1_500_000), 6, 1, Cluster.MAINNET, "x", 0).formatted())
    }

    @Test fun telemetryFormatting() {
        assertEquals("250.00 GB", Telemetry.gb(250_000_000_000))
        assertEquals(NetActivity.UNAVAILABLE, Telemetry.activity(null, 5))
        assertEquals(NetActivity.IDLE, Telemetry.activity(5, 5))
        assertEquals(NetActivity.ACTIVE, Telemetry.activity(5, 9))
    }

    @Test fun oreBotIsTextOnly() {
        val src = File("src/main/java/com/edgeore/app/bot/OreBot.kt").readText()
        for (b in listOf("WalletCoordinator", "WalletConnection", "ActivityResultSender", "SolanaRpc", "sign(", "signTransaction", "OreDeploy", "TransferCoordinator", "BalanceRpc")) assertFalse(b, src.contains(b))
        val tips = OreBot.tips(false, false, true, "Devnet")
        assertTrue(tips.any { it.contains("can't sign") })
        assertFalse(tips.any { Regex("earn|free ORE|reward(?! )", RegexOption.IGNORE_CASE).containsMatchIn(it) && !it.contains("no token reward") })
    }
}
