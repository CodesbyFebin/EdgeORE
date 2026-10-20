package com.edgeore.app.edge

import com.edgeore.app.crypto.Base58
import com.edgeore.app.ore.OreV3
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

object RoundBytes {
    fun round(
        id: Long,
        deployed: List<Long>,
        slotHash: ByteArray,
        motherlode: Long = 0,
        reward: Long = OreV3.GRAMS_PER_ORE,
        topMiner: ByteArray? = null,
    ): ByteArray {
        val rng = RoundRules.rng(slotHash)
        val w = rng?.let { RoundRules.winningSquare(it) }
        val (adm, prot) = if (w != null) RoundRules.fees(deployed, w) else 0L to 0L
        val top =
            topMiner ?: if (w != null && !RoundRules.isSolo(id, w)) Base58.decodePublicKey(RoundRules.SPLIT_ADDRESS) else ByteArray(32)
        val b = ByteBuffer.allocate(RoundRules.ROUND_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        b.put(RoundRules.DISC_ROUND.toByte()).put(ByteArray(7))
        b.putLong(id)
        deployed.forEach { b.putLong(it) }
        deployed.forEach { b.putLong(it) } // mass
        deployed.forEach { b.putLong(if (it > 0) 1 else 0) } // count
        b.put(slotHash)
        b.putLong(1_000_000) // expires_at
        b.putLong(motherlode)
        b.put(ByteArray(32))
        b.putLong(reward)
        repeat(24) { b.putLong(0) }
        b.putLong(prot)
        b.putLong(deployed.sum() - adm - prot)
        b.putLong(deployed.count { it > 0 }.toLong())
        b.put(top)
        return b.array()
    }

    fun miner(
        authority: ByteArray,
        deployed: List<Long>,
        cumulative: List<Long>,
        roundId: Long,
    ): ByteArray {
        val b = ByteBuffer.allocate(RoundRules.MINER_PREFIX + 64).order(ByteOrder.LITTLE_ENDIAN)
        b.put(103.toByte()).put(ByteArray(7)).put(authority)
        b.putLong(0).putLong(0).putLong(0)
        deployed.forEach { b.putLong(it) }
        deployed.forEach { b.putLong(it) }
        cumulative.forEach { b.putLong(it) }
        b.putLong(roundId)
        return b.array()
    }

    /** slot_hash whose rng (XOR of words) is [r]: first word r, rest zero. */
    fun hashFor(r: Long): ByteArray =
        ByteBuffer
            .allocate(32)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putLong(r)
            .array()
}

class EdgeDifferentiatorsTest {
    private val res = javaClass.classLoader!!

    @Test fun rngWinningSquareAndMotherlodeMatchRustReferenceOnRealMainnetRounds() {
        val v = JSONObject(res.getResource("ore/mainnet-settled-rounds.json").readText())
        val ref =
            res
                .getResource("ore/settled-rounds-rust-reference.txt")
                .readText()
                .lines()
                .filter { it.isNotBlank() }
                .associate { l -> l.split(" ").let { it[0].toLong() to it } }
        val rounds = v.getJSONArray("rounds")
        assertTrue(rounds.length() >= 3)
        for (i in 0 until rounds.length()) {
            val o = rounds.getJSONObject(i)
            assertEquals(OreV3.PROGRAM_ID, o.getString("owner"))
            val round = RoundRules.decodeRound(Base64.getDecoder().decode(o.getString("dataBase64")))
            assertEquals(o.getLong("roundId"), round.id)
            assertEquals(OreV3.roundAddress(round.id), o.getString("address"))
            val r = RoundRules.rng(round.slotHash)!!
            val want = ref.getValue(round.id)
            assertEquals(want[1], "%016x".format(r))
            assertEquals(want[2].toInt(), RoundRules.winningSquare(r) + 1)
            assertEquals(want[3].toBoolean(), RoundRules.didHitMotherlode(r))
        }
    }

    @Test fun distributionMaskHasTenSoloTilesAndMatchesRealSplitMarkers() {
        for (id in 0L until 300L) assertEquals(10, Integer.bitCount(RoundRules.distributionMask(id)))
        val v = JSONObject(res.getResource("ore/mainnet-settled-rounds.json").readText()).getJSONArray("rounds")
        val split = Base58.decodePublicKey(RoundRules.SPLIT_ADDRESS)
        for (i in 0 until v.length()) {
            val round = RoundRules.decodeRound(Base64.getDecoder().decode(v.getJSONObject(i).getString("dataBase64")))
            val w = RoundRules.winningSquare(RoundRules.rng(round.slotHash)!!)
            assertEquals("round ${round.id}", !RoundRules.isSolo(round.id, w), round.topMiner.contentEquals(split))
            // Live accounts: losing squares are refunded minus the 10% protocol fee and 1% admin fee;
            // totals match calculate_fees exactly.
            val (adm, prot) = RoundRules.fees(round.deployed, w)
            assertEquals(prot, round.totalVaulted)
            assertEquals(round.totalDeployed - adm - prot, round.totalReturnedSol)
        }
    }

    @Test fun rngIsNoneForEmptyOrFullHash() {
        assertNull(RoundRules.rng(ByteArray(32)))
        assertNull(RoundRules.rng(ByteArray(32) { -1 }))
    }

    @Test fun unsignedModuloUsedForHighBitRng() {
        val r = -1L // u64::MAX
        assertEquals(
            java.lang.Long
                .remainderUnsigned(r, 25)
                .toInt(),
            RoundRules.winningSquare(r),
        )
        assertEquals(((ULong.MAX_VALUE % 25u).toInt()), RoundRules.winningSquare(r))
    }

    @Test fun splitAddressDecodes() {
        assertEquals(32, Base58.decodePublicKey(RoundRules.SPLIT_ADDRESS).size)
    }

    @Test fun minerOutcomeFollowsCheckpointRules() {
        val r = 7L // winning square index 7
        val deployed =
            MutableList(25) { 0L }.also {
                it[7] = 1_000_000_000L
                it[3] = 500_000_000L
            }
        val split = Base58.decodePublicKey(RoundRules.SPLIT_ADDRESS)
        val round = RoundRules.decodeRound(RoundBytes.round(5, deployed, RoundBytes.hashFor(r), topMiner = split))
        val mine =
            MutableList(25) { 0L }.also {
                it[7] = 250_000_000L
                it[3] = 100_000_000L
            }
        val miner = RoundRules.decodeMiner(RoundBytes.miner(ByteArray(32) { 1 }, mine, List(25) { 0L }, 5))
        val o = RoundRules.minerOutcome(round, miner, split)
        // Winner: 250M * (1e9 - 1e7) / 1e9 = 247.5M. Loser sq 3: admin 5M, protocol 49.5M, returned 445.5M -> 100M*445.5/500 = 89.1M.
        assertEquals(247_500_000L + 89_100_000L, o.rewardsSol)
        assertEquals(OreV3.GRAMS_PER_ORE / 4, o.rewardsOre)
        assertTrue(o.split)
        assertFalse(o.refunded)
    }

    @Test fun topMinerSampleDecidesNonSplitWinner() {
        val r = 7L
        val deployed = MutableList(25) { 0L }.also { it[7] = 100L }
        val round = RoundRules.decodeRound(RoundBytes.round(5, deployed, RoundBytes.hashFor(r), topMiner = ByteArray(32)))
        val sample = RoundRules.topMinerSample(r, 100)
        val inRange =
            RoundRules.decodeMiner(
                RoundBytes.miner(
                    ByteArray(32),
                    deployed.map {
                        if (it >
                            0
                        ) {
                            50L
                        } else {
                            0L
                        }
                    },
                    List(25) { if (it == 7) sample else 0L },
                    5,
                ),
            )
        val split = Base58.decodePublicKey(RoundRules.SPLIT_ADDRESS)
        assertTrue(RoundRules.minerOutcome(round, inRange, split).wonTopMiner)
        val out =
            RoundRules.decodeMiner(
                RoundBytes.miner(
                    ByteArray(32),
                    deployed.map {
                        if (it >
                            0
                        ) {
                            50L
                        } else {
                            0L
                        }
                    },
                    List(25) { if (it == 7) sample + 1 else 0L },
                    5,
                ),
            )
        assertFalse(RoundRules.minerOutcome(round, out, split).wonTopMiner)
    }

    @Test fun unsettledRoundRefunds() {
        val round = RoundRules.decodeRound(RoundBytes.round(5, List(25) { 0L }, ByteArray(32)))
        val miner = RoundRules.decodeMiner(RoundBytes.miner(ByteArray(32), List(25) { 0L }, List(25) { 0L }, 5))
        assertTrue(RoundRules.minerOutcome(round, miner, ByteArray(32)).refunded)
    }

    @Test fun tileInsightCrowdingAndEv() {
        val deployed = List(25) { if (it == 0) 10_000_000_000L else 100_000_000L }
        val tiles = TileInsight.tiles(deployed, List(25) { 1L }, 10_000_000, OreV3.GRAMS_PER_ORE, 0)
        assertEquals(TileInsight.Crowding.CROWDED, tiles[0].crowding)
        assertEquals(TileInsight.Crowding.LIGHT, tiles[1].crowding)
        assertTrue(tiles[1].expectedOreGrams > tiles[0].expectedOreGrams)
        assertEquals(tiles[0].expectedSolKeptLamports, tiles[1].expectedSolKeptLamports)
        assertEquals(0.10504, TileInsight.expectedSolCostFraction(), 1e-9)
        assertEquals(TileInsight.Crowding.EMPTY, TileInsight.tiles(List(25) { 0L }, List(25) { 0L }, 1, 1, 0)[3].crowding)
        assertEquals("estimate, not a promise", TileInsight.LABEL)
    }

    @Test fun presetsOnlyReturnMasks() {
        val deployed = List(25) { (it + 1) * 1_000_000L }
        assertEquals((1 shl 25) - 1, StrategyPreset.SPREAD.mask(deployed))
        assertEquals(0b111, StrategyPreset.SOLO.mask(deployed))
        val skip = StrategyPreset.SKIP_CROWDED.mask(deployed)
        assertTrue(Integer.bitCount(skip) in 1..24)
        assertEquals(0, StrategyPreset.SOLO.mask(null))
    }

    @Test fun explainerIsPlainAndMakesNoIncomeClaims() {
        val t = TileInsight.tiles(List(25) { 5_000_000L }, List(25) { 2L }, 10_000_000, OreV3.GRAMS_PER_ORE, 0)[4]
        val text = RoundExplainer.tile(t) + RoundExplainer.round(9, List(25) { 5_000_000L }, "00:30") + RoundExplainer.NOT_CONFIGURED
        for (bad in listOf("APR", "yield", "profit", "guarantee", "official", "income")) assertFalse(bad, text.contains(bad, true))
        assertTrue(text.contains("Not configured"))
        assertTrue(text.contains("sign"))
    }

    @Test fun alertsFireOncePerRound() {
        assertEquals(RoundAlerts.Kind.ROUND_ENDING, RoundAlerts.roundEnding(3, 20, null)!!.kind)
        assertNull(RoundAlerts.roundEnding(3, 20, 3))
        assertNull(RoundAlerts.roundEnding(3, 90, null))
        val round = RoundRules.decodeRound(RoundBytes.round(3, List(25) { 0L }, RoundBytes.hashFor(1), motherlode = 5))
        val kinds = RoundAlerts.settled(round, RoundRules.MinerOutcome(0, 1, 0, true, false, false)).map { it.kind }
        assertEquals(listOf(RoundAlerts.Kind.MOTHERLODE_HIT, RoundAlerts.Kind.YOU_WON), kinds)
    }

    @Test fun historyTotalsOnlySettledRows() {
        val t =
            History.totals(
                listOf(
                    History.Row(1, "a", 10_000_000, 9_000_000, 5, "Proof verified"),
                    History.Row(2, "b", 10_000_000, null, null, "Not settled"),
                ),
            )
        assertEquals(1, t.rounds)
        assertEquals(1, t.unsettled)
        assertEquals(-1_000_000, t.netSolLamports)
    }

    private fun proof(): String {
        val deployed = MutableList(25) { 0L }.also { it[7] = 1_000_000_000L }
        val bytes = RoundBytes.round(42, deployed, RoundBytes.hashFor(7))
        val m = RoundBytes.miner(ByteArray(32) { 2 }, deployed.map { it / 10 }, List(25) { 0L }, 42)
        return RoundProof.build(OreV3.roundAddress(42), bytes, 99, "test", "minerX", m)
    }

    @Test fun roundProofReDerivesAndRejectsTamper() {
        val p = proof()
        val ok = RoundProof.check(p, listOf(RoundProof.digest(p)), OreV3::roundAddress)
        assertTrue(ok.failures.toString(), ok.accepted)
        assertEquals(8, ok.winningSquare)
        assertTrue(ok.unverified.isNotEmpty())
        // Not in a signed receipt
        assertFalse(RoundProof.check(p, emptyList(), OreV3::roundAddress).accepted)
        // Claimed result edited, digest recomputed (as an attacker would): re-derivation catches it
        val j = JSONObject(p)
        j.getJSONObject("derived").put("winningSquare", 3)
        val t = j.toString()
        assertFalse(RoundProof.check(t, listOf(RoundProof.digest(t)), OreV3::roundAddress).accepted)
        // Wrong PDA
        val k = JSONObject(p).put("roundAddress", OreV3.roundAddress(41)).toString()
        assertFalse(RoundProof.check(k, listOf(RoundProof.digest(k)), OreV3::roundAddress).accepted)
    }
}

class PlanningCalculatorTest {
    @Test fun breakEvenMath() {
        val r = PlanningCalculator.compute(PlanningCalculator.Inputs(0.01, 10, 25, 0.49, 0.5, 0.000005))
        // per tile 0.0004; share 0.0004/0.4904; ore = 25/25*share
        assertEquals(0.0004 / 0.4904, r.expectedOrePerRound, 1e-12)
        assertEquals(0.01 * 0.10504 + 0.000005, r.expectedSolCostPerRound, 1e-12)
        assertEquals(r.expectedSolCostPerRound / r.expectedOrePerRound, r.breakEvenOrePriceSol!!, 1e-9)
        assertEquals(0.1, r.totalSolCommitted, 1e-12)
        assertEquals(1.0, r.chanceAnyTileWinsPerRound, 0.0)
        assertEquals("assumptions, not a promise", PlanningCalculator.LABEL)
    }
}

class ProtocolGuardTest {
    private val cap =
        JSONObject(javaClass.classLoader!!.getResource("ore/mainnet-protocol-capture.json").readText()).getJSONObject("accounts")

    private fun b(n: String) = Base64.getDecoder().decode(cap.getJSONObject(n).getString("dataBase64"))

    @Test fun liveCaptureMatchesThisBuild() {
        assertEquals(ProtocolGuard.Result.Match, ProtocolGuard.check(b("program"), b("programdataHead"), b("config")))
        val c = OreProofV0.decodeConfig(b("config"))
        assertEquals(100L, c.adminBps)
        assertEquals(1000L, c.protocolBps)
        assertEquals(40, b("board").size)
    }

    @Test fun redeployOrFeeChangeRaisesBanner() {
        val head = b("programdataHead").also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putLong(4, 460_000_000L) }
        assertTrue(ProtocolGuard.check(b("program"), head, b("config")) is ProtocolGuard.Result.Changed)
        val cfg = b("config").also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putLong(8 + 64, 200L) }
        assertTrue(ProtocolGuard.check(b("program"), b("programdataHead"), cfg) is ProtocolGuard.Result.Changed)
        assertTrue(ProtocolGuard.check(null, null, null) is ProtocolGuard.Result.Unknown)
    }

    @Test fun deployWalletTipIsOneLineAndMakesNoSafetyClaim() {
        val tip = com.edgeore.app.ui.screens.DEPLOY_WALLET_TIP
        assertTrue(tip.contains("small deploy wallet"))
        assertFalse(tip.contains("\n"))
        listOf("quantum", "safe", "guarantee", "SKR").forEach { assertFalse(tip.contains(it, ignoreCase = true)) }
    }
}
