package com.edgeore.app

import com.edgeore.app.edge.Crew
import com.edgeore.app.edge.RoundRules
import com.edgeore.app.edge.Strategy
import com.edgeore.app.edge.StrategyPlan
import com.edgeore.app.edge.StrategyPlaybook
import com.edgeore.app.edge.StrategyRules
import com.edgeore.app.ore.OreSnapshot
import com.edgeore.app.ore.OreV3
import com.edgeore.app.ore.RoundClock
import com.edgeore.app.solana.OreDeployLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every board value here comes from a recorded mainnet read (fixture), never from invented numbers. */
class StrategyPlaybookTest {
    private val f = OreV3Test.Fixture("mainnet-round-437127.json")
    private val snap =
        OreSnapshot(
            "https://api.mainnet-beta.solana.com",
            OreV3.decodeBoard(f.board.owner, f.board.data),
            f.slot("board"),
            OreV3.decodeTreasury(f.treasury.owner, f.treasury.data),
            OreV3.decodeRound(f.round.owner, f.round.data),
            f.slot("round"),
            0L,
            null,
        )
    private val playbook = "src/main/java/com/edgeore/app/edge/StrategyPlaybook.kt"
    private val cap = OreDeployLimits.MAINNET_CAP_LAMPORTS
    private val all = (1 shl 25) - 1

    private fun plan(
        s: Strategy,
        threshold: Long = 0,
        crew: List<Int>? = null,
    ) = StrategyPlaybook.plan(s, snap, StrategyPlaybook.Inputs(cap, threshold, crew, 0L))

    @Test fun capIsStillPointZeroOneSol() = assertEquals(10_000_000L, cap)

    @Test fun soloHunterPrefillsExactlyTheTenSoloTilesOfTheLiveRound() {
        val p = plan(Strategy.SOLO_HUNTER) as StrategyPlan.Fill
        assertEquals(RoundRules.distributionMask(snap.round.id), p.mask)
        assertEquals(10, Integer.bitCount(p.mask))
        assertNull("keeps the user's own amount", p.totalLamports)
    }

    @Test fun quietSquaresAreTheThreeLeastCrowdedOnTheBoard() {
        val p = plan(Strategy.QUIET_SQUARES) as StrategyPlan.Fill
        assertEquals(3, Integer.bitCount(p.mask))
        val d = snap.round.deployedLamports
        val chosen = (0 until 25).filter { p.mask and (1 shl it) != 0 }
        val others = (0 until 25) - chosen.toSet()
        assertTrue(others.all { o -> chosen.all { c -> d[c] <= d[o] } })
    }

    @Test fun splitSweepCoversTheFifteenSplitTilesWithinTheCap() {
        val p = plan(Strategy.SPLIT_SWEEP) as StrategyPlan.Fill
        val solo = RoundRules.distributionMask(snap.round.id)
        assertEquals(15, Integer.bitCount(p.mask))
        assertEquals(0, p.mask and solo)
        assertEquals(all, p.mask or solo)
        val total = p.totalLamports!!
        assertTrue(total <= cap)
        assertEquals(0L, total % 15)
        assertEquals(cap / 15 * 15, total)
        assertTrue(StrategyPlaybook.splitSweep(0b111, cap) is StrategyPlan.Disabled)
        assertTrue(StrategyPlaybook.splitSweep(all and solo.inv(), 10) is StrategyPlan.Disabled)
    }

    @Test fun motherlodeWatchOnlyOpensAtOrAboveThreshold() {
        val pool = snap.treasury.motherlodeGrams
        assertTrue(pool > 0)
        assertTrue((plan(Strategy.MOTHERLODE_WATCH, pool) as StrategyPlan.Gate).open)
        assertTrue((plan(Strategy.MOTHERLODE_WATCH, pool - 1) as StrategyPlan.Gate).open)
        val closed = plan(Strategy.MOTHERLODE_WATCH, pool + 1) as StrategyPlan.Gate
        assertFalse(closed.open)
        assertTrue(closed.reason.startsWith("Sit out"))
        assertFalse("no threshold means sit out", (plan(Strategy.MOTHERLODE_WATCH, 0) as StrategyPlan.Gate).open)
    }

    @Test fun crewBlanketNeedsFullCoverageWithoutOverlap() {
        val keys =
            (1..6).map { i ->
                com.edgeore.app.crypto.Base58
                    .encode(ByteArray(32) { i.toByte() })
            }
        for (n in 1..6) {
            val members = keys.take(n).map { Crew.Member(it, 1, 1_000_000) }
            val full = Crew.reassign(Crew.Plan("c", "x", 1, keys[0], members), keys.take(n).toSet())!!
            val p = plan(Strategy.CREW_BLANKET, crew = full.members.map { it.mask }) as StrategyPlan.Fill
            assertEquals(all, p.mask)
            full.members.forEach { assertTrue(it.totalLamports <= cap) }
        }
        assertTrue(plan(Strategy.CREW_BLANKET, crew = listOf(0b11, 0b10, all)) is StrategyPlan.Disabled)
        assertTrue(plan(Strategy.CREW_BLANKET, crew = listOf(0b1111)) is StrategyPlan.Disabled)
        assertTrue(plan(Strategy.CREW_BLANKET, crew = null) is StrategyPlan.Disabled)
    }

    @Test fun lateLookRemindsOnlyNearTheEndAndNeverActs() {
        val near = StrategyPlaybook.lateLook(RoundClock.Live(StrategyPlaybook.LATE_LOOK_SLOTS, 16))
        assertTrue((near as StrategyPlan.Remind).now)
        assertFalse((StrategyPlaybook.lateLook(RoundClock.Live(200, 80)) as StrategyPlan.Remind).now)
        assertFalse((StrategyPlaybook.lateLook(RoundClock.WaitingForFirstDeploy) as StrategyPlan.Remind).now)
        assertFalse((StrategyPlaybook.lateLook(RoundClock.Ended(3)) as StrategyPlan.Remind).now)
        assertTrue(plan(Strategy.LATE_LOOK) is StrategyPlan.Remind)
    }

    @Test fun withoutARealBoardReadNothingIsPrefilled() {
        Strategy.entries.filter { it != Strategy.CREW_BLANKET }.forEach { s ->
            val p = StrategyPlaybook.plan(s, null, StrategyPlaybook.Inputs(cap, 1, null, 0L))
            assertTrue(s.name, p is StrategyPlan.Disabled)
        }
    }

    @Test fun everyPresetRuleIsVerifiedAtThePinnedCommit() {
        assertEquals("48c203bd75db3cc45105ec29d8f8db719e5a2263", StrategyRules.SOURCE_COMMIT)
        Strategy.entries.forEach { assertTrue(it.name, it.verified) }
        val doc = File("../docs/edge/strategy-rules.md").readText()
        assertTrue(doc.contains(StrategyRules.SOURCE_COMMIT))
        Strategy.entries.forEach { assertTrue(it.title, doc.contains(it.title)) }
    }

    @Test fun wordingHasNoYieldAprOrPredictionAndCarriesTheDisclaimer() {
        assertEquals(
            "Every square has the same 1-in-25 chance. No preset raises your odds or promises profit.",
            Strategy.DISCLAIMER,
        )
        val words = "yield|apr|apy|predict\\w*|forecast\\w*|guarantee\\w*|earn\\w*|income|roi|profit\\w*"
        val banned = Regex("(?i)\\b($words)\\b")
        val texts = Strategy.entries.flatMap { listOf(it.title, it.tradeOff) }
        texts.forEach { assertFalse(it, banned.containsMatchIn(it)) }
        val section = File("src/main/java/com/edgeore/app/ui/screens/StrategyPanels.kt").readText()
        val strings = Regex("\"([^\"]*)\"").findAll(section).map { it.groupValues[1] }.toList()
        strings.forEach { assertFalse(it, banned.containsMatchIn(it)) }
        val src = File(playbook).readText().replace(Strategy.DISCLAIMER, "")
        Regex("\"([^\"]*)\"").findAll(src).forEach { assertFalse(it.value, banned.containsMatchIn(it.groupValues[1])) }
    }

    @Test fun playbookCannotSignOrSubmit() {
        val src = File(playbook).readText()
        listOf("Wallet", "Mwa", "ReceiptSigner", "signer", "submit")
            .plus(listOf("prepareOreDeploy", "launch", "Worker", "AlarmManager"))
            .forEach { assertFalse(it, src.contains(it)) }
        val vm = File("src/main/java/com/edgeore/app/EdgeOreViewModel.kt").readText()
        val wiring = vm.substring(vm.indexOf("fun applyStrategy"), vm.indexOf("private fun motherlodeSitOut"))
        assertTrue(wiring.contains("OreDeployLimits.MAINNET_CAP_LAMPORTS"))
        assertTrue(wiring.contains("_ore.value.snapshot"))
        listOf(
            "submit",
            "signReviewed",
            "prepareOreDeploy",
            "rounds =",
            "riskAcknowledged",
        ).forEach { assertFalse(it, wiring.contains(it)) }
    }
}
