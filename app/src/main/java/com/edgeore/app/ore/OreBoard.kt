package com.edgeore.app.ore

import com.edgeore.app.solana.AccountReader
import com.edgeore.app.solana.SlotRate

/** One read of the live ORE v3 board, with where and when it was read. Every number on the ORE screen comes from here. */
data class OreSnapshot(
    val rpc: String,
    val board: OreV3.Board,
    val boardSlot: Long,
    val treasury: OreV3.Treasury,
    val round: OreV3.Round,
    val roundSlot: Long,
    val observedAtMs: Long,
    val slotRate: SlotRate?,
    /** ORE Config as read with the round; null when it could not be read (then round length is shown as unknown). */
    val config: OreV3.Config? = null,
)

/** Where the current round stands, from Board.start_slot / end_slot and the RPC's slot. */
sealed interface RoundClock {
    /** end_slot is u64::MAX: the round starts with its first deploy (program/src/deploy.rs). */
    data object WaitingForFirstDeploy : RoundClock

    /** [secondsLeft] is null when no slot rate was measured; then only slots are shown. */
    data class Live(
        val slotsLeft: Long,
        val secondsLeft: Long?,
    ) : RoundClock

    /** Past end_slot: the round is waiting to be reset (intermission, program/src/reset.rs). */
    data class Ended(
        val slotsPast: Long,
    ) : RoundClock
}

object OreClock {
    /**
     * [observedSlot] is the slot the RPC reported with the read; [elapsedMs] is how long ago (this phone's clock) it was read.
     * The current slot is estimated only with a measured [rate]; without one the observed slot is used as is.
     */
    fun at(
        board: OreV3.Board,
        observedSlot: Long,
        elapsedMs: Long,
        rate: SlotRate?,
    ): RoundClock {
        if (board.endSlot == OreV3.END_SLOT_WAITING) return RoundClock.WaitingForFirstDeploy
        val est = observedSlot + if (rate != null && elapsedMs > 0) (elapsedMs / rate.msPerSlot).toLong() else 0L
        val left = board.endSlot - est
        if (left <= 0) return RoundClock.Ended(-left)
        return RoundClock.Live(left, rate?.let { Math.round(left * it.msPerSlot / 1000.0) })
    }

    fun mmss(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)

    fun label(c: RoundClock): String =
        when (c) {
            RoundClock.WaitingForFirstDeploy -> "Waiting for the first deploy"
            is RoundClock.Live -> c.secondsLeft?.let { mmss(it) } ?: "${c.slotsLeft} slots"
            is RoundClock.Ended -> "Ended · awaiting reset"
        }
}

object OreFormat {
    /** Lamports as SOL with up to 3 decimals (the precision the ORE web board shows), never rounded up to look larger. */
    fun sol(lamports: Long): String =
        java.math.BigDecimal
            .valueOf(lamports, 9)
            .setScale(3, java.math.RoundingMode.DOWN)
            .stripTrailingZeros()
            .toPlainString()

    fun ore(grams: Long): String =
        java.math.BigDecimal
            .valueOf(grams, 11)
            .setScale(2, java.math.RoundingMode.DOWN)
            .stripTrailingZeros()
            .toPlainString()
}

/** Reads Board and Treasury in one call, then the current Round (its PDA from Board.round_id), and checks they agree. */
class OreObserver(
    private val reader: AccountReader,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    companion object {
        /** Screenshot tests must not depend on live RPC availability or changing mainnet rounds. */
        @androidx.annotation.VisibleForTesting
        @Volatile internal var testSource: (suspend () -> OreSnapshot)? = null
    }

    suspend fun read(): OreSnapshot {
        testSource?.let { return it() }
        val first = reader.accounts(listOf(OreV3.BOARD_ADDRESS, OreV3.TREASURY_ADDRESS))
        val b = first.accounts[0] ?: throw OreV3.DecodeException("Board account not found on this RPC's cluster")
        val t = first.accounts[1] ?: throw OreV3.DecodeException("Treasury account not found on this RPC's cluster")
        val board = OreV3.decodeBoard(b.owner, b.data)
        val treasury = OreV3.decodeTreasury(t.owner, t.data)
        val second = reader.accounts(listOf(OreV3.roundAddress(board.roundId)))
        val r = second.accounts[0] ?: throw OreV3.DecodeException("Round ${board.roundId} account not found")
        val round = OreV3.decodeRound(r.owner, r.data)
        if (round.id !=
            board.roundId
        ) {
            throw OreV3.DecodeException("Round account id ${round.id} does not match Board.round_id ${board.roundId}")
        }
        // Round length lives in ORE Config (deploy.rs, reset.rs). A failed read hides timing; it never invents it.
        val config =
            runCatching {
                reader
                    .accounts(listOf(OreV3.CONFIG_ADDRESS))
                    .accounts
                    .firstOrNull()
                    ?.let { OreV3.decodeConfig(it.owner, it.data) }
            }.getOrNull()
        return OreSnapshot(
            reader.endpoint,
            board,
            first.slot,
            treasury,
            round,
            second.slot,
            nowMs(),
            reader.slotRate(),
            config,
        )
    }
}

/** ORE screen state. [snapshot] is the last successful read; [error] the last failure (the old snapshot stays, labelled with its time). */
data class OreState(
    val rpc: String = DEFAULT_RPC,
    val snapshot: OreSnapshot? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val rpcError: String? = null,
) {
    companion object {
        /** Public mainnet RPC, used only for read-only observation. Configurable on the ORE screen. */
        const val DEFAULT_RPC = "https://api.mainnet-beta.solana.com"
    }
}

/** Advisor chat. [backend] is null when no AI path is configured ("Not configured"). */
data class AdvisorMessage(
    val fromUser: Boolean,
    val text: String,
    val time: Long,
)

data class AdvisorState(
    val messages: List<AdvisorMessage> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
)
