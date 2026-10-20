package com.edgeore.app

import com.edgeore.app.edge.FeeMath
import com.edgeore.app.edge.StrategyPlan
import com.edgeore.app.edge.StrategyPlaybook
import com.edgeore.app.ore.OreV3
import com.edgeore.app.ore.RoundClock
import com.edgeore.app.ui.Destination
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

/** Miner-first home: fee wording/calculator, Config-read round timing, and unset motherlode limit = unlimited. */
class MinerFirstTest {
    private val cfg =
        JSONObject(javaClass.classLoader!!.getResource("ore/mainnet-config.json").readText()).let {
            OreV3.decodeConfig(it.getString("owner"), Base64.getDecoder().decode(it.getString("dataBase64")))
        }

    @Test fun mineIsTheFirstTabAndHome() {
        assertEquals(Destination.Mine, Destination.nav.first())
        assertEquals("Mine", Destination.Mine.label)
    }

    @Test fun configReadFromRecordedMainnetAccount() {
        // Recorded 20 Oct 2026 from 9c9X7aDR…: round 300 slots, intermission 60 (not the constants in consts.rs).
        assertEquals(300L, cfg.roundSlots)
        assertEquals(60L, cfg.intermissionSlots)
        assertEquals(100L, cfg.adminFeeBps)
        assertEquals(1000L, cfg.protocolFeeBps)
    }

    @Test fun configDecoderRefusesWrongShape() {
        val bad = runCatching { OreV3.decodeConfig(OreV3.PROGRAM_ID, ByteArray(10)) }
        assertTrue(bad.isFailure)
        val wrongOwner = runCatching { OreV3.decodeConfig("11111111111111111111111111111111", ByteArray(OreV3.CONFIG_SIZE)) }
        assertTrue(wrongOwner.isFailure)
    }

    @Test fun lateLookWindowFollowsConfig() {
        assertEquals(45L, StrategyPlaybook.lateLookSlots(cfg))
        assertEquals(StrategyPlaybook.LATE_LOOK_SLOTS, StrategyPlaybook.lateLookSlots(null))
        val p = StrategyPlaybook.lateLook(RoundClock.Live(44, null), StrategyPlaybook.lateLookSlots(cfg)) as StrategyPlan.Remind
        assertTrue(p.now)
    }

    @Test fun noHardcodedRoundLengthInMinerHome() {
        val src = File("src/main/java/com/edgeore/app/ui/screens/MinerHome.kt").readText()
        listOf("150", "200", "240", "300").forEach { assertFalse("hardcoded $it", Regex("\\b$it\\b").containsMatchIn(src)) }
    }

    @Test fun losingSquareRefundMinusFees() {
        // calculate_fees: admin = max(t/100,1); protocol = max((t-admin)/10,1); refund = t - admin - protocol.
        assertEquals(10_000_000L, FeeMath.adminFee(1_000_000_000L))
        assertEquals(99_000_000L, FeeMath.protocolFee(1_000_000_000L))
        assertEquals(891_000_000L, FeeMath.losingRefund(1_000_000_000L, 1_000_000_000L))
        assertEquals(445_500_000L, FeeMath.losingRefund(500_000_000L, 1_000_000_000L))
        assertEquals(1L, FeeMath.adminFee(50L))
        assertEquals(0L, FeeMath.losingRefund(0L, 100L))
        assertTrue(FeeMath.LOSING.contains("refunded minus a 10% fee, plus a 1% admin fee"))
    }

    @Test fun unsetMotherlodeLimitIsUnlimited() {
        val big = 10_000L * OreV3.GRAMS_PER_ORE
        assertTrue((StrategyPlaybook.motherlode(big, 1, null) as StrategyPlan.Gate).open)
        assertTrue((StrategyPlaybook.motherlode(big, 1, 0) as StrategyPlan.Gate).open)
        assertFalse((StrategyPlaybook.motherlode(big, 1, big - 1) as StrategyPlan.Gate).open)
        assertTrue((StrategyPlaybook.motherlode(big, 1, big) as StrategyPlan.Gate).open)
        assertFalse(StrategyPlaybook.limitSet(null))
        assertFalse(StrategyPlaybook.limitSet(0))
    }

    @Test fun noYieldOrSigningLanguageOnMinerHome() {
        val src = File("src/main/java/com/edgeore/app/ui/screens/MinerHome.kt").readText().lowercase()
        listOf("apr", "apy", "yield", "income", "profit", "guaranteed").forEach { assertFalse(it, Regex("\\b$it\\b").containsMatchIn(src)) }
        assertTrue(src.contains("not affiliated"))
        assertFalse(src.contains("signtransaction") || src.contains("signandsend"))
    }
}
