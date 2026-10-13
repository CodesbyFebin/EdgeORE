package com.edgeore.app.solana

/**
 * What the owner is told after the single submit. Derived only from the durable operation state that
 * [TransferCoordinator.observe] wrote from getSignatureStatuses. Nothing here sends or resends.
 */
sealed interface ConfirmationView {
    /** Still polling: no confirmed/finalized status yet and the blockhash may still land. */
    data object Waiting : ConfirmationView

    /** getSignatureStatuses reported confirmed or finalized without an error. */
    data class Confirmed(
        val finalized: Boolean,
    ) : ConfirmationView

    /** The cluster reported an error for this signature. */
    data class Failed(
        val error: String,
    ) : ConfirmationView

    /** Polling stopped without a confirmed status (expired blockhash, send error or poll budget used up). */
    data object NotConfirmed : ConfirmationView
}

object Confirmation {
    /** Poll spacing and budget. 2 s x 75 = 150 s, past the ~60-90 s life of a blockhash. */
    const val POLL_INTERVAL_MS = 2_000L
    const val MAX_POLLS = 75

    const val NOT_CONFIRMED_TEXT = "Not confirmed: check Solscan before retrying"

    /** True once the blockhash expired with nothing found (the coordinator records "> last valid" on that read). */
    fun expired(op: PendingOperation): Boolean = op.lastObservation?.contains("> last valid") == true

    /** Stop polling once settled either way or once the blockhash has expired. */
    fun done(op: PendingOperation): Boolean {
        val expiredUnknown = op.state == OpState.OUTCOME_UNKNOWN && expired(op)
        return op.state in SETTLED || expiredUnknown
    }

    private val SETTLED = setOf(OpState.CONFIRMED, OpState.FINALIZED, OpState.FAILED)

    /** [pollsUsed] >= [MAX_POLLS]: budget spent; an unsettled operation then shows as not confirmed. */
    fun view(
        op: PendingOperation,
        pollsUsed: Int = 0,
    ): ConfirmationView =
        when (op.state) {
            OpState.CONFIRMED -> ConfirmationView.Confirmed(false)
            OpState.FINALIZED -> ConfirmationView.Confirmed(true)
            OpState.FAILED -> ConfirmationView.Failed(op.lastObservation ?: "FAILED")
            OpState.OUTCOME_UNKNOWN, OpState.SUBMITTED, OpState.SUBMIT_ATTEMPTED ->
                if (expired(op) || pollsUsed >= MAX_POLLS) ConfirmationView.NotConfirmed else ConfirmationView.Waiting
            else -> ConfirmationView.NotConfirmed
        }

    /** Solscan transaction link for the operation's own cluster (mainnet has no query; others name the cluster). */
    fun solscanUrl(
        signature: String,
        cluster: String,
    ): String {
        require(signature.isNotEmpty() && signature.all { it in BASE58_ALPHABET }) { "INVALID_SIGNATURE" }
        val base = "https://solscan.io/tx/$signature"
        return when (cluster) {
            Cluster.MAINNET.id -> base
            Cluster.DEVNET.id -> "$base?cluster=devnet"
            Cluster.TESTNET.id -> "$base?cluster=testnet"
            else -> throw IllegalArgumentException("UNKNOWN_CLUSTER")
        }
    }

    private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    fun label(v: ConfirmationView): String =
        when (v) {
            ConfirmationView.Waiting -> "Waiting for confirmation (read-only status checks, no resend)"
            is ConfirmationView.Confirmed -> if (v.finalized) "Confirmed (finalized)" else "Confirmed"
            is ConfirmationView.Failed -> "Failed on chain: ${v.error}"
            ConfirmationView.NotConfirmed -> NOT_CONFIRMED_TEXT
        }
}
