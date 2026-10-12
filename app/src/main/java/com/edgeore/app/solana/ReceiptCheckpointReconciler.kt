package com.edgeore.app.solana

/**
 * Gate in front of an on-chain receipt checkpoint (programs `record_receipt_checkpoint`). The checkpoint anchors the
 * receipt chain as it stands; it must never paper over an operation whose ledger outcome is uncertain.
 *
 * Order, and nothing else: (1) any Unknown/Submitted operation blocks the checkpoint unless the USER asked for a
 * re-poll; (2) the re-poll goes through the coordinator's observe (chain read, ledger write); (3) only a ledger state
 * that actually changed emits a receipt update; (4) the checkpoint is Ready only when nothing is uncertain any more.
 * The checkpoint never writes the ledger and never turns Unknown into anything by itself.
 */
class ReceiptCheckpointReconciler(
    private val operations: () -> List<PendingOperation>,
    private val observe: suspend (id: String) -> Unit,
    private val find: (id: String) -> PendingOperation?,
    private val emitReceiptUpdate: (before: PendingOperation, after: PendingOperation) -> Unit,
) {
    sealed interface Result {
        data object Ready : Result

        data class Blocked(
            val uncertainIds: List<String>,
            val reason: String,
        ) : Result
    }

    private fun uncertain(): List<PendingOperation> =
        operations().filter {
            when (OperationOutcome.of(it.state)) {
                OperationOutcome.Unknown, OperationOutcome.Submitted -> true
                is OperationOutcome.NotSent, is OperationOutcome.NeverSent,
                is OperationOutcome.Landed, OperationOutcome.Failed,
                -> false
            }
        }

    suspend fun prepare(userRequestedRepoll: Boolean): Result {
        val pending = uncertain()
        if (pending.isNotEmpty() && userRequestedRepoll) {
            for (before in pending) {
                observe(before.id)
                val after = find(before.id)
                if (after != null && after.state != before.state) emitReceiptUpdate(before, after)
            }
        }
        val still = if (pending.isEmpty()) pending else uncertain()
        return when {
            still.isEmpty() -> Result.Ready
            !userRequestedRepoll -> Result.Blocked(still.map { it.id }, BLOCKED_NEEDS_REPOLL)
            else -> Result.Blocked(still.map { it.id }, BLOCKED_AFTER_REPOLL)
        }
    }

    companion object {
        const val BLOCKED_NEEDS_REPOLL = "Outcome unknown. Re-check these operations on chain before checkpointing."
        const val BLOCKED_AFTER_REPOLL =
            "Still not observed on chain after re-check; the checkpoint waits. Unknown is never resolved automatically."
    }
}
