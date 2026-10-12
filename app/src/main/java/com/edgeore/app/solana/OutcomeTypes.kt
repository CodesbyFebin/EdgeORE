package com.edgeore.app.solana

/**
 * Result of checking a reviewed message's blockhash against a fresh block-height read. Sealed, so every caller must
 * handle all three cases in an exhaustive `when`; a missing branch is a compile error, not a silent default.
 */
sealed interface BlockhashCheckResult {
    /** At least [marginBlocks] blocks remain before lastValidBlockHeight. */
    data class Valid(val height: Long, val lastValidBlockHeight: Long, val remainingBlocks: Long) : BlockhashCheckResult
    /** Past or within the margin of lastValidBlockHeight. Only never-sent bytes may be released on this. */
    data class Expired(val height: Long, val lastValidBlockHeight: Long) : BlockhashCheckResult
    /** Height could not be read, so validity cannot be shown. Never treated as valid or as expired. */
    data object Unavailable : BlockhashCheckResult

    companion object {
        fun of(height: Long?, lastValidBlockHeight: Long, marginBlocks: Long = 0): BlockhashCheckResult = when {
            height == null -> Unavailable
            height > lastValidBlockHeight - marginBlocks -> Expired(height, lastValidBlockHeight)
            else -> Valid(height, lastValidBlockHeight, lastValidBlockHeight - height)
        }
    }
}

/**
 * What the user may be told about one operation, derived from its durable [OpState]. Sealed and exhaustive.
 * [Unknown] never resolves on its own: only a chain observation (via [observed]) may move it, and NOT_FOUND or
 * an expired blockhash keeps it Unknown.
 */
sealed interface OperationOutcome {
    /** No bytes have left the app yet (reviewed, signing, signed-but-unsent). */
    data class NotSent(val state: OpState) : OperationOutcome
    /** Never sent and finished: refused, abandoned, or expired before sending. Reservation released. */
    data class NeverSent(val state: OpState) : OperationOutcome
    /** The RPC accepted the bytes. Not a confirmation. */
    data object Submitted : OperationOutcome
    /** Bytes may or may not have reached the cluster. */
    data object Unknown : OperationOutcome
    data class Landed(val finalized: Boolean) : OperationOutcome
    data object Failed : OperationOutcome

    companion object {
        fun of(state: OpState): OperationOutcome = when (state) {
            OpState.REVIEWED, OpState.SIGNING, OpState.SIGNED -> NotSent(state)
            OpState.EXPIRED, OpState.REFUSED, OpState.ABANDONED -> NeverSent(state)
            OpState.SUBMIT_ATTEMPTED, OpState.OUTCOME_UNKNOWN -> Unknown
            OpState.SUBMITTED -> Submitted
            OpState.CONFIRMED -> Landed(finalized = false)
            OpState.FINALIZED -> Landed(finalized = true)
            OpState.FAILED -> Failed
        }

        /**
         * Applies one chain observation. Unknown/Submitted move only on a positive status (landed or failed);
         * NOT_FOUND (null) or a time-based check never resolves them, whatever the block height.
         */
        fun observed(current: OperationOutcome, landed: Boolean?, failed: Boolean, finalized: Boolean): OperationOutcome = when (current) {
            Unknown, Submitted -> when {
                failed -> Failed
                landed == true -> Landed(finalized)
                else -> current
            }
            is Landed -> if (finalized) Landed(true) else current
            is NotSent, is NeverSent, Failed -> current
        }
    }
}
