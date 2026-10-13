package com.edgeore.app.edge

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ORE v3 round rules, re-implemented read-only from regolith-labs/ore at commit 48c203bd75db3cc45105ec29d8f8db719e5a2263:
 *   api/src/state/round.rs   Round layout, rng(), winning_square(), top_miner_sample(), did_hit_motherlode(), calculate_fees()
 *   api/src/state/miner.rs   Miner layout (authority, auto_return, checkpoint_id, checkpoint_fee, deployed, mass, cumulative, round_id)
 *   program/src/checkpoint.rs  per-miner SOL return and ORE share
 *   api/src/consts.rs        SPLIT_ADDRESS
 * Pure Kotlin (no Android) so tools/receipt-checker compiles this exact file. Nothing here signs or sends anything.
 * Unsigned u64 values are held in Long and compared/divided with the unsigned helpers.
 */
object RoundRules {
    const val SOURCE_COMMIT = "48c203bd75db3cc45105ec29d8f8db719e5a2263"
    const val PROGRAM_ID = "oreV3EG1i9BEgiAJ8b177Z2S2rMarzak4NMv1kULvWv"
    const val SQUARES = 25
    const val DISC_ROUND = 109
    const val ROUND_SIZE = 8 + 8 + 3 * 25 * 8 + 32 + 8 + 8 + 32 + 25 * 8 + 8 + 8 + 8 + 32

    /** api/src/consts.rs SPLIT_ADDRESS = SpLiT11111111111111111111111111111111111112 (decoded bytes are checked in tests). */
    const val SPLIT_ADDRESS = "SpLiT11111111111111111111111111111111111112"

    /** Miner prefix this file reads: disc(8) + authority(32) + auto_return + checkpoint_id + checkpoint_fee + 3x[u64;25] + round_id. */
    const val MINER_PREFIX = 8 + 32 + 8 + 8 + 8 + 3 * 25 * 8 + 8

    data class FullRound(
        val id: Long,
        val deployed: List<Long>,
        val count: List<Long>,
        val slotHash: ByteArray,
        val expiresAt: Long,
        val motherlode: Long,
        val rewards: List<Long>,
        val totalVaulted: Long,
        val totalReturnedSol: Long,
        val totalMiners: Long,
        val topMiner: ByteArray,
    ) {
        val totalDeployed: Long get() = deployed.sum()
        val topMinerReward: Long get() = rewards.sum()
    }

    data class MinerRound(
        val authority: ByteArray,
        val deployed: List<Long>,
        val cumulative: List<Long>,
        val roundId: Long,
    )

    class RuleException(
        message: String,
    ) : Exception(message)

    private fun le(data: ByteArray) = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

    fun decodeRound(data: ByteArray): FullRound {
        if (data.size != ROUND_SIZE) throw RuleException("Round has ${data.size} bytes; the pinned layout has $ROUND_SIZE")
        if ((data[0].toInt() and 0xff) != DISC_ROUND || (1 until 8).any { data[it].toInt() != 0 }) {
            throw RuleException("Round discriminator is not $DISC_ROUND")
        }
        val b = le(data).also { it.position(8) }
        val id = b.long
        val deployed = List(SQUARES) { b.long }
        repeat(SQUARES) { b.long } // mass
        val count = List(SQUARES) { b.long }
        val slotHash = ByteArray(32).also { b.get(it) }
        val expiresAt = b.long
        val motherlode = b.long
        b.position(b.position() + 32) // rent_payer
        val rewards = List(SQUARES) { b.long }
        val vaulted = b.long
        val returned = b.long
        val miners = b.long
        val top = ByteArray(32).also { b.get(it) }
        if (deployed.any { it < 0 } || rewards.any { it < 0 }) throw RuleException("Round has an out-of-range value")
        return FullRound(id, deployed, count, slotHash, expiresAt, motherlode, rewards, vaulted, returned, miners, top)
    }

    /** Reads the Miner prefix only (discriminator byte is checked by the caller's owner/disc check where available). */
    fun decodeMiner(data: ByteArray): MinerRound {
        if (data.size < MINER_PREFIX) throw RuleException("Miner has ${data.size} bytes; at least $MINER_PREFIX expected")
        val b = le(data).also { it.position(8) }
        val auth = ByteArray(32).also { b.get(it) }
        b.long
        b.long
        b.long // auto_return, checkpoint_id, checkpoint_fee
        val deployed = List(SQUARES) { b.long }
        repeat(SQUARES) { b.long } // mass
        val cumulative = List(SQUARES) { b.long }
        val roundId = b.long
        return MinerRound(auth, deployed, cumulative, roundId)
    }

    /** round.rs rng(): None for an all-zero or all-0xFF slot_hash; otherwise XOR of the four little-endian u64 words. */
    fun rng(slotHash: ByteArray): Long? {
        require(slotHash.size == 32)
        if (slotHash.all { it.toInt() == 0 } || slotHash.all { it.toInt() == -1 }) return null
        val b = le(slotHash)
        return b.long xor b.long xor b.long xor b.long
    }

    fun winningSquare(rng: Long): Int =
        java.lang.Long
            .remainderUnsigned(rng, SQUARES.toLong())
            .toInt()

    fun didHitMotherlode(rng: Long): Boolean = java.lang.Long.remainderUnsigned(java.lang.Long.reverse(rng), 500L) == 0L

    fun topMinerSample(
        rng: Long,
        deployedOnWinner: Long,
    ): Long = if (deployedOnWinner == 0L) 0L else java.lang.Long.remainderUnsigned(java.lang.Long.reverse(rng), deployedOnWinner)

    /** round.rs calculate_fees(): (admin, protocol). */
    fun fees(
        deployed: List<Long>,
        winning: Int,
    ): Pair<Long, Long> {
        var admin = 0L
        var protocol = 0L
        deployed.forEachIndexed { i, d ->
            if (d > 0) {
                val a = maxOf(d / 100, 1)
                admin += a
                if (i != winning) protocol += maxOf((d - a).coerceAtLeast(0) / 10, 1)
            }
        }
        return admin to protocol
    }

    /**
     * round.rs distribution_mask(): keccak256(id LE) seeds a Fisher-Yates shuffle of 0..24 (2 bytes per draw, u16 LE,
     * j = r % (i+1)); the first 10 shuffled indices get bit 1 = "solo" (top-miner) tiles, the other 15 are split.
     * Depends only on the round id, so it is known before the round ends.
     */
    fun distributionMask(roundId: Long): Int {
        var randomness =
            keccak(
                ByteBuffer
                    .allocate(8)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putLong(roundId)
                    .array(),
            )
        val idx = IntArray(SQUARES) { it }
        var off = 0
        for (i in SQUARES - 1 downTo 1) {
            if (off + 2 > randomness.size) {
                randomness = keccak(randomness)
                off = 0
            }
            val r = (randomness[off].toInt() and 0xff) or ((randomness[off + 1].toInt() and 0xff) shl 8)
            val j = r % (i + 1)
            val t = idx[i]
            idx[i] = idx[j]
            idx[j] = t
            off += 2
        }
        return (0 until 10).fold(0) { m, k -> m or (1 shl idx[k]) }
    }

    fun isSolo(
        roundId: Long,
        square: Int,
    ): Boolean = distributionMask(roundId) and (1 shl square) != 0

    private fun keccak(b: ByteArray): ByteArray {
        val d =
            org.bouncycastle.crypto.digests
                .KeccakDigest(256)
        d.update(b, 0, b.size)
        return ByteArray(32).also { d.doFinal(it, 0) }
    }

    private fun mulDiv(
        a: Long,
        b: Long,
        c: Long,
    ): Long =
        java.math.BigInteger
            .valueOf(a)
            .multiply(java.math.BigInteger.valueOf(b))
            .divide(java.math.BigInteger.valueOf(c))
            .toLong()

    data class MinerOutcome(
        val rewardsSol: Long,
        /** ORE (grams) from the top-miner/split reward. */
        val rewardsOre: Long,
        val motherloadOre: Long,
        val wonTopMiner: Boolean,
        val split: Boolean,
        val refunded: Boolean,
    )

    /** checkpoint.rs, the reward branch, for one miner and one settled round. */
    fun minerOutcome(
        round: FullRound,
        miner: MinerRound,
        splitAddress: ByteArray,
    ): MinerOutcome {
        val r = rng(round.slotHash) ?: return MinerOutcome(miner.deployed.sum(), 0, 0, false, false, true)
        val w = winningSquare(r)
        val split = round.topMiner.contentEquals(splitAddress)
        var sol = 0L
        var ore = 0L
        var ml = 0L
        var won = false
        for (i in 0 until SQUARES) {
            val m = miner.deployed[i]
            if (m == 0L) continue
            val total = round.deployed[i]
            if (total < m) throw RuleException("Miner deployed more on square ${i + 1} than the round total")
            val admin = maxOf(total / 100, 1)
            if (i == w) {
                sol += mulDiv(m, (total - admin).coerceAtLeast(0), total)
                if (split) {
                    ore = mulDiv(round.topMinerReward, m, total)
                } else {
                    val s = topMinerSample(r, total)
                    if (s >= miner.cumulative[i] && s < miner.cumulative[i] + m) {
                        ore = round.topMinerReward
                        won = true
                    }
                }
                if (round.motherlode > 0) ml = mulDiv(round.motherlode, m, total)
            } else {
                val protocol = maxOf((total - admin).coerceAtLeast(0) / 10, 1)
                sol += mulDiv(m, (total - admin - protocol).coerceAtLeast(0), total)
            }
        }
        return MinerOutcome(sol, ore, ml, won, split, false)
    }
}
