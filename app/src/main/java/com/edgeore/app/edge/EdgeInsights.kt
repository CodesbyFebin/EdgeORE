package com.edgeore.app.edge

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Tile insight: crowding and an expected-value estimate per square, from the live Round read only.
 * Information only. Nothing here builds, signs or sends a transaction. Math: docs/edge/tile-insight-ev.md.
 * Label on screen: "estimate, not a promise".
 */
/**
 * One wording and one calculator for fees, from calculate_fees() in api/src/state/round.rs at regolith-labs/ore 48c203bd:
 * every square with SOL pays a 1% admin fee (deployed / 100, at least 1 lamport); a losing square also pays 10% of what is
 * left (at least 1 lamport); the rest is refunded.
 */
object FeeMath {
    const val SOURCE = "calculate_fees, regolith-labs/ore 48c203bd"
    const val LOSING =
        "Losing squares: your SOL is refunded minus a 10% fee, plus a 1% admin fee (about 89.1% comes back)."
    const val WINNING = "Winning square: your SOL is refunded minus the 1% admin fee."

    fun adminFee(squareTotal: Long): Long = if (squareTotal <= 0) 0 else maxOf(squareTotal / 100, 1)

    fun protocolFee(squareTotal: Long): Long = if (squareTotal <= 0) 0 else maxOf((squareTotal - adminFee(squareTotal)) / 10, 1)

    /** SOL refunded on a losing square to someone who put [mine] of the square's [squareTotal] (checkpoint.rs pro rata). */
    fun losingRefund(
        mine: Long,
        squareTotal: Long,
    ): Long {
        if (mine <= 0 || squareTotal <= 0) return 0
        require(mine <= squareTotal)
        val keep = (squareTotal - adminFee(squareTotal) - protocolFee(squareTotal)).coerceAtLeast(0)
        return java.math.BigInteger
            .valueOf(mine)
            .multiply(java.math.BigInteger.valueOf(keep))
            .divide(java.math.BigInteger.valueOf(squareTotal))
            .toLong()
    }
}

object TileInsight {
    const val LABEL = "estimate, not a promise"

    /** Winning probability per square assumed uniform: round.rs winning_square = rng % 25. */
    const val P_WIN = 1.0 / RoundRules.SQUARES

    /** round.rs did_hit_motherlode: reverse_bits(rng) % 500 == 0. */
    const val P_MOTHERLODE = 1.0 / 500

    /** Admin fee 1% on every square; non-winning squares also pay 10% of the remainder (calculate_fees / checkpoint.rs). */
    const val KEEP_IF_WIN = 0.99
    const val KEEP_IF_LOSE = 0.99 * 0.9

    enum class Crowding { EMPTY, LIGHT, AVERAGE, CROWDED }

    data class Tile(
        val square: Int,
        val deployedLamports: Long,
        val miners: Long,
        val crowding: Crowding,
        /** Ratio to the board's mean per-square SOL; null when the board is empty. */
        val crowdRatio: Double?,
        /** Estimated SOL kept for [stakeLamports] on this square (same for every square; fees do not depend on the pick). */
        val expectedSolKeptLamports: Long,
        /** Estimated ORE (grams) for [stakeLamports] on this square, before any later deploys dilute it. */
        val expectedOreGrams: Long,
        val stakeLamports: Long,
    )

    fun crowding(ratio: Double?): Crowding =
        when {
            ratio == null || ratio == 0.0 -> Crowding.EMPTY
            ratio < 0.75 -> Crowding.LIGHT
            ratio <= 1.5 -> Crowding.AVERAGE
            else -> Crowding.CROWDED
        }

    /**
     * [rewardGrams]: ORE per round to the winning square (reset.rs mints min(ONE_ORE, cap remaining)); the caller passes the
     * value it can justify, 0 if unknown. [motherlodeGrams]: Treasury.motherlode as read.
     */
    fun tiles(
        deployed: List<Long>,
        miners: List<Long>,
        stakeLamports: Long,
        rewardGrams: Long,
        motherlodeGrams: Long,
    ): List<Tile> {
        require(deployed.size == RoundRules.SQUARES && miners.size == RoundRules.SQUARES)
        val mean = deployed.sum().toDouble() / RoundRules.SQUARES
        val keep = (stakeLamports * (P_WIN * KEEP_IF_WIN + (1 - P_WIN) * KEEP_IF_LOSE)).toLong()
        return deployed.mapIndexed { i, d ->
            val ratio = if (mean > 0) d / mean else null
            val share = if (stakeLamports > 0) stakeLamports.toDouble() / (d + stakeLamports) else 0.0
            val ore = P_WIN * share * (rewardGrams + P_MOTHERLODE * motherlodeGrams)
            Tile(i, d, miners[i], crowding(ratio), ratio, keep, ore.toLong(), stakeLamports)
        }
    }

    /** Expected SOL cost as a fraction of the stake: 1 - (1/25 * 0.99 + 24/25 * 0.891) = 0.10504. */
    fun expectedSolCostFraction(): Double = 1 - (P_WIN * KEEP_IF_WIN + (1 - P_WIN) * KEEP_IF_LOSE)

    fun pct(x: Double): String = BigDecimal(x * 100).setScale(1, RoundingMode.HALF_UP).toPlainString() + "%"
}

/** Presets only choose squares for the exact-message review. They never deploy, sign or repeat; Rounds stays 1. */
enum class StrategyPreset(
    val title: String,
    val blurb: String,
) {
    SPREAD("Spread", "All 25 squares, split evenly."),
    SOLO("Solo picks", "The 3 least-crowded squares."),
    SKIP_CROWDED("Skip crowded", "Every square except the crowded ones."),
    ;

    /** Returns the 25-bit square mask to pre-fill. 0 means nothing to pre-fill (caller shows why). */
    fun mask(deployed: List<Long>?): Int {
        val all = (1 shl RoundRules.SQUARES) - 1
        if (this == SPREAD || deployed == null) return if (this == SPREAD) all else 0
        val tiles = TileInsight.tiles(deployed, List(RoundRules.SQUARES) { 0L }, 0, 0, 0)
        return when (this) {
            SOLO -> tiles.sortedWith(compareBy({ it.deployedLamports }, { it.square })).take(3).fold(0) { m, t -> m or (1 shl t.square) }
            SKIP_CROWDED -> tiles.filter { it.crowding != TileInsight.Crowding.CROWDED }.fold(0) { m, t -> m or (1 shl t.square) }
            SPREAD -> all
        }
    }
}

/** Plain-words explanation of a round or tile. Rule-based; never signs; used whenever no model is configured. */
object RoundExplainer {
    const val NOT_CONFIGURED = "AI model: Not configured. Showing the built-in rule-based explanation."

    fun tile(t: TileInsight.Tile): String =
        buildString {
            val sol = sol(t.deployedLamports)
            append("Square ${t.square + 1} has $sol SOL from ${t.miners} miner(s). ")
            append(
                when (t.crowding) {
                    TileInsight.Crowding.EMPTY -> "Nobody is on it yet, so any ORE it wins would be shared with fewer people. "
                    TileInsight.Crowding.LIGHT -> "It is less crowded than the average square. "
                    TileInsight.Crowding.AVERAGE -> "It is about as crowded as the average square. "
                    TileInsight.Crowding.CROWDED -> "It is more crowded than average, so a win here is shared among more SOL. "
                },
            )
            append("Every square has the same 1 in 25 chance to win. ")
            append("If it does not win, about 10.9% of the SOL on it is taken as fees; if it wins, 1% is. ")
            append("Numbers are an ${TileInsight.LABEL}. Nothing is deployed unless you review and sign.")
        }

    fun round(
        id: Long,
        deployed: List<Long>,
        clockLabel: String,
    ): String {
        val busiest = deployed.indices.maxByOrNull { deployed[it] }
        return buildString {
            append("Round #$id: ${sol(deployed.sum())} SOL deployed across ${deployed.count { it > 0 }} of 25 squares. ")
            append("Time: $clockLabel. ")
            if (busiest != null && deployed[busiest] > 0) append("Most SOL is on square ${busiest + 1}. ")
            append("When it ends, one square is chosen from the round's on-chain entropy; that square's miners share the ORE. ")
            append("On average about ${TileInsight.pct(TileInsight.expectedSolCostFraction())} of deployed SOL goes to fees. ")
            append("This is an explanation, not advice.")
        }
    }

    fun sol(l: Long): String =
        BigDecimal
            .valueOf(l, 9)
            .setScale(3, RoundingMode.DOWN)
            .stripTrailingZeros()
            .toPlainString()
}

/** Events for local notifications, derived only from two consecutive read-only polls. */
object RoundAlerts {
    enum class Kind { ROUND_ENDING, MOTHERLODE_HIT, YOU_WON }

    data class Alert(
        val kind: Kind,
        val roundId: Long,
        val text: String,
    )

    /** [secondsLeft] from the board clock; fires once per round when it first drops to <= [thresholdS]. */
    fun roundEnding(
        roundId: Long,
        secondsLeft: Long?,
        alreadyFor: Long?,
        thresholdS: Long = 30,
    ): Alert? =
        if (secondsLeft != null && secondsLeft in 1..thresholdS && alreadyFor != roundId) {
            Alert(Kind.ROUND_ENDING, roundId, "Round #$roundId ends in about ${secondsLeft}s.")
        } else {
            null
        }

    fun settled(
        round: RoundRules.FullRound,
        outcome: RoundRules.MinerOutcome?,
    ): List<Alert> {
        val out = mutableListOf<Alert>()
        if (round.motherlode > 0) out += Alert(Kind.MOTHERLODE_HIT, round.id, "Motherlode hit in round #${round.id}.")
        if (outcome != null && (outcome.rewardsOre > 0 || outcome.motherloadOre > 0)) {
            out += Alert(Kind.YOU_WON, round.id, "Your squares won ORE in round #${round.id}. Open Round Proof to check it.")
        }
        return out
    }
}

/** P&L from the user's own deploy receipts plus Round Proof results. No leaderboard server. */
object History {
    data class Row(
        val roundId: Long?,
        val signature: String?,
        val deployedLamports: Long,
        val solBackLamports: Long?,
        val oreGrams: Long?,
        val status: String,
    )

    data class Totals(
        val deployedLamports: Long,
        val solBackLamports: Long,
        val oreGrams: Long,
        val rounds: Int,
        val unsettled: Int,
    ) {
        val netSolLamports: Long get() = solBackLamports - deployedLamports
    }

    fun totals(rows: List<Row>): Totals {
        val settled = rows.filter { it.solBackLamports != null }
        return Totals(
            settled.sumOf { it.deployedLamports },
            settled.sumOf { it.solBackLamports ?: 0 },
            settled.sumOf { it.oreGrams ?: 0 },
            settled.size,
            rows.size - settled.size,
        )
    }
}

/**
 * Planning calculator. Every input is the user's assumption; outputs are averages under ORE v3 rules at 48c203bd
 * ("assumptions, not a promise"). No dollar figures, no growth over time, no APR.
 */
object PlanningCalculator {
    const val LABEL = "assumptions, not a promise"

    data class Inputs(
        val solPerRound: Double,
        val rounds: Int,
        val tiles: Int,
        /** SOL already on each chosen tile (typical), assumed. */
        val othersSolPerTile: Double,
        /** Assumed ORE price in SOL. */
        val orePriceSol: Double,
        /** Network/priority fee per deploy in SOL, assumed. */
        val txFeeSol: Double,
        /** ORE to the winning tile per round (reset.rs: up to 1). */
        val rewardOre: Double = 1.0,
    )

    data class Result(
        val expectedSolCostPerRound: Double,
        val expectedOrePerRound: Double,
        val breakEvenOrePriceSol: Double?,
        val expectedNetSolPerRoundAtAssumedPrice: Double,
        val totalSolCommitted: Double,
        val chanceAnyTileWinsPerRound: Double,
    )

    fun compute(i: Inputs): Result {
        require(i.tiles in 1..25 && i.rounds >= 1 && i.solPerRound >= 0)
        val per = i.solPerRound / i.tiles
        val cost = i.solPerRound * TileInsight.expectedSolCostFraction() + i.txFeeSol
        val share = if (per > 0) per / (i.othersSolPerTile + per) else 0.0
        val ore = i.tiles * TileInsight.P_WIN * share * i.rewardOre
        return Result(
            cost,
            ore,
            if (ore > 0) cost / ore else null,
            ore * i.orePriceSol - cost,
            i.solPerRound * i.rounds,
            i.tiles / 25.0,
        )
    }
}
