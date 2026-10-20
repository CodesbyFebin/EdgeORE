package com.edgeore.app.edge

import com.edgeore.app.ore.OreClock
import com.edgeore.app.ore.OreSnapshot
import com.edgeore.app.ore.RoundClock

/**
 * The ORE v3 rules each strategy depends on, checked against regolith-labs/ore at [SOURCE_COMMIT] and the live chain
 * (docs/edge/strategy-rules.md). A strategy whose rule is not verified ships disabled with [UNVERIFIED].
 */
object StrategyRules {
    const val SOURCE_COMMIT = "48c203bd75db3cc45105ec29d8f8db719e5a2263"
    const val UNVERIFIED = "Unverified rule"

    enum class Rule(
        val verified: Boolean,
        val source: String,
    ) {
        SOLO_SPLIT(true, "api/src/state/round.rs distribution_mask / is_split_reward"),
        MOTHERLODE(true, "api/src/state/treasury.rs Treasury.motherlode; program/src/reset.rs did_hit_motherlode"),
        ROUND_TIMING(true, "api/src/state/board.rs start_slot/end_slot; Config.round_slots; program/src/deploy.rs"),
        FEES(true, "api/src/state/round.rs calculate_fees; program/src/checkpoint.rs"),
        CREW(true, "EdgeORE Crew plan (local); no ORE rule beyond deploy masks"),
    }
}

enum class Strategy(
    val title: String,
    val tradeOff: String,
    val rules: List<StrategyRules.Rule>,
) {
    SOLO_HUNTER(
        "Solo Hunter",
        "Only the 10 ★ solo squares this round. If one wins, one miner on it takes the round's ORE instead of " +
            "sharing it, so most of the time you get none.",
        listOf(StrategyRules.Rule.SOLO_SPLIT),
    ),
    QUIET_SQUARES(
        "Quiet Squares",
        "The 3 least-crowded squares on the live board. Less SOL to share with if one wins, but the board can " +
            "change before the round ends.",
        listOf(StrategyRules.Rule.FEES),
    ),
    MOTHERLODE_WATCH(
        "Motherlode Watch",
        "Review only opens when the motherlode pool is at or above your threshold; otherwise sit out. " +
            "A round hits the motherlode rarely (1 in 500 in the program).",
        listOf(StrategyRules.Rule.MOTHERLODE),
    ),
    SPLIT_SWEEP(
        "Split Sweep",
        "All 15 split squares with small, equal amounts that stay within the cap. If one wins, its ORE is " +
            "shared among everyone on it.",
        listOf(StrategyRules.Rule.SOLO_SPLIT, StrategyRules.Rule.FEES),
    ),
    CREW_BLANKET(
        "Crew Blanket",
        "Your crew covers all 25 squares with no overlap. Each member reviews and signs only their own share; " +
            "on the 24 losing squares the SOL is refunded minus a 10% fee, plus a 1% admin fee.",
        listOf(StrategyRules.Rule.CREW, StrategyRules.Rule.FEES),
    ),
    LATE_LOOK(
        "Late Look",
        "A reminder in the app near the end of the round to re-check crowding. It does not deploy or sign; " +
            "you may run out of time to review.",
        listOf(StrategyRules.Rule.ROUND_TIMING),
    ),
    ;

    val verified: Boolean get() = rules.all { it.verified }

    companion object {
        const val DISCLAIMER =
            "Every square has the same 1-in-25 chance. No preset raises your odds or promises profit."
    }
}

/** What a strategy pre-fills. Nothing here deploys or signs; a [Fill] only feeds the normal exact-message review. */
sealed interface StrategyPlan {
    data class Fill(
        val mask: Int,
        /** Null keeps the user's amount. Always within [capLamports]. */
        val totalLamports: Long?,
    ) : StrategyPlan

    data class SitOut(
        val reason: String,
    ) : StrategyPlan

    data class Disabled(
        val reason: String,
    ) : StrategyPlan

    data class Gate(
        val open: Boolean,
        val reason: String,
    ) : StrategyPlan

    data class Remind(
        val now: Boolean,
        val reason: String,
    ) : StrategyPlan
}

object StrategyPlaybook {
    const val LATE_LOOK_SLOTS = 40L
    private const val LATE_LOOK_PERCENT = 15L
    private const val PERCENT = 100L
    private const val SPLIT_TILES = 15
    private const val QUIET = 3
    private const val ALL = (1 shl RoundRules.SQUARES) - 1
    private const val RECHECK = "Re-check crowding before you review."
    private const val UPDATE = "use the plan update."
    private const val NEEDS_BOARD = "Needs a live board read first; nothing was changed."

    /** Inputs besides the board. [capLamports] is the per-deploy cap (0.01 SOL on mainnet). */
    data class Inputs(
        val capLamports: Long,
        val motherlodeThresholdGrams: Long = 0,
        /** Members' masks of a verified crew plan, or null. */
        val crewMasks: List<Int>? = null,
        val nowMs: Long = System.currentTimeMillis(),
        /** Upper motherlode limit (like the autominer's max_motherlode). Null or 0 means not set = unlimited. */
        val motherlodeLimitGrams: Long? = null,
    )

    /** [snap] is the real board read (null before the first read); nothing is invented without it. */
    fun plan(
        s: Strategy,
        snap: OreSnapshot?,
        inp: Inputs,
    ): StrategyPlan =
        when {
            !s.verified -> StrategyPlan.Disabled(StrategyRules.UNVERIFIED)
            s == Strategy.CREW_BLANKET -> crew(inp.crewMasks)
            snap == null -> StrategyPlan.Disabled(NEEDS_BOARD)
            else -> fromBoard(s, snap, inp)
        }

    private fun fromBoard(
        s: Strategy,
        snap: OreSnapshot,
        inp: Inputs,
    ): StrategyPlan {
        val solo = RoundRules.distributionMask(snap.round.id)
        return when (s) {
            Strategy.SOLO_HUNTER -> StrategyPlan.Fill(solo, null)
            Strategy.QUIET_SQUARES -> StrategyPlan.Fill(quietest(snap.round.deployedLamports), null)
            Strategy.SPLIT_SWEEP -> splitSweep(ALL and solo.inv(), inp.capLamports)
            Strategy.MOTHERLODE_WATCH ->
                motherlode(snap.treasury.motherlodeGrams, inp.motherlodeThresholdGrams, inp.motherlodeLimitGrams)
            Strategy.LATE_LOOK -> lateLook(clock(snap, inp.nowMs), lateLookSlots(snap.config))
            Strategy.CREW_BLANKET -> crew(inp.crewMasks)
        }
    }

    fun text(p: StrategyPlan): String =
        when (p) {
            is StrategyPlan.Fill -> "pre-filled"
            is StrategyPlan.SitOut -> p.reason
            is StrategyPlan.Disabled -> p.reason
            is StrategyPlan.Gate -> p.reason
            is StrategyPlan.Remind -> p.reason
        }

    fun gateOpen(
        snap: OreSnapshot?,
        thresholdGrams: Long,
    ): Boolean = snap != null && (motherlode(snap.treasury.motherlodeGrams, thresholdGrams) as StrategyPlan.Gate).open

    fun clock(
        snap: OreSnapshot,
        nowMs: Long,
    ) = OreClock.at(snap.board, snap.boardSlot, nowMs - snap.observedAtMs, snap.slotRate)

    fun quietest(deployed: List<Long>): Int =
        deployed.indices
            .sortedWith(compareBy({ deployed[it] }, { it }))
            .take(QUIET)
            .fold(0) { m, i -> m or (1 shl i) }

    fun splitSweep(
        splitMask: Int,
        capLamports: Long,
    ): StrategyPlan {
        val n = Integer.bitCount(splitMask)
        if (n != SPLIT_TILES) return StrategyPlan.Disabled("Expected 15 split squares, found $n.")
        val per = capLamports / n
        return if (per <= 0) StrategyPlan.Disabled("Cap too small to split.") else StrategyPlan.Fill(splitMask, per * n)
    }

    fun motherlode(
        poolGrams: Long,
        thresholdGrams: Long,
        limitGrams: Long? = null,
    ): StrategyPlan =
        // An unset (null) or zero limit means unlimited, never "0 ORE" (the ORE motherlode-default bug).
        if (limitGrams != null && limitGrams > 0 && poolGrams > limitGrams) {
            StrategyPlan.Gate(false, "Sit out: the motherlode pool is above your limit.")
        } else if (poolGrams >= thresholdGrams && thresholdGrams > 0) {
            StrategyPlan.Gate(true, "Motherlode pool is at or above your threshold. You can open the review.")
        } else {
            StrategyPlan.Gate(false, "Sit out: the motherlode pool is below your threshold.")
        }

    /**
     * The Late Look window: the last 15% of Config.round_slots as read from chain. [LATE_LOOK_SLOTS] is used only when
     * the Config account could not be read.
     */
    fun lateLookSlots(config: com.edgeore.app.ore.OreV3.Config?): Long =
        config?.let { maxOf(1L, it.roundSlots * LATE_LOOK_PERCENT / PERCENT) } ?: LATE_LOOK_SLOTS

    fun lateLook(
        c: RoundClock,
        window: Long = LATE_LOOK_SLOTS,
    ): StrategyPlan =
        when (c) {
            is RoundClock.Live ->
                if (c.slotsLeft <= window) {
                    StrategyPlan.Remind(true, "Late Look: about ${OreClock.label(c)} left. $RECHECK")
                } else {
                    StrategyPlan.Remind(false, "Late Look reminds you in the app at about $window slots left.")
                }
            RoundClock.WaitingForFirstDeploy -> StrategyPlan.Remind(false, "The round starts with its first deploy.")
            is RoundClock.Ended -> StrategyPlan.Remind(false, "This round has ended; wait for the next one.")
        }

    /** Full coverage, no overlap, else a reason. The crew's own plan is used; members still sign their own share. */
    fun crew(masks: List<Int>?): StrategyPlan {
        val union = masks.orEmpty().fold(0) { u, m -> u or m }
        val overlap = masks.orEmpty().sumOf { Integer.bitCount(it) } != Integer.bitCount(union)
        return when {
            masks.isNullOrEmpty() -> StrategyPlan.Disabled("Open or create a verified crew plan first.")
            overlap -> StrategyPlan.Disabled("Crew plan overlaps; ask the creator to sign a plan update.")
            union != ALL -> StrategyPlan.Disabled("Crew plan covers ${Integer.bitCount(union)} of 25 squares; $UPDATE")
            else -> StrategyPlan.Fill(union, null)
        }
    }
}
