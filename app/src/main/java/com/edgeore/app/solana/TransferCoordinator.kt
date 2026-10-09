package com.edgeore.app.solana

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import java.time.Instant

sealed interface ChainStatus {
    data object NotFound : ChainStatus
    data object Processed : ChainStatus
    data object Confirmed : ChainStatus
    data object Finalized : ChainStatus
    data class Failed(val error: String) : ChainStatus
}

/** The three RPC reads/writes the transaction lifecycle needs. Implemented by [SolanaRpc]; faked in tests. */
interface ChainGateway {
    /** Sends exact signed bytes. Any exception means the outcome is unknown, never "not sent". */
    suspend fun send(signedTx: ByteArray): String
    suspend fun status(signature: String): ChainStatus
    /** Current block height at finalized commitment. */
    suspend fun blockHeight(): Long
}

/**
 * Owns every wallet-operation transition. Rules:
 * - single flight: one network send at a time, and every step is a compare-and-set on the durable state;
 * - SUBMIT_ATTEMPTED is fsynced before the send;
 * - a timeout or error during send is OUTCOME_UNKNOWN, never a clean failure;
 * - recovery only reads (status, block height) and never re-sends;
 * - an expired operation needs a fresh review: this class never builds a message.
 */
class TransferCoordinator(
    private val store: OperationStore,
    private val gateway: ChainGateway,
    private val clock: () -> Instant = Instant::now,
) {
    private val sendMutex = Mutex()
    private val observeMutex = Mutex()

    sealed interface Outcome {
        data class Done(val op: PendingOperation) : Outcome
        data class Busy(val reason: String) : Outcome
        data class NotAllowed(val reason: String) : Outcome
    }

    private fun now() = clock().toString()

    /** Called once at start-up, before any UI action. No network. */
    fun recoverAfterRestart(): List<PendingOperation> {
        val changed = mutableListOf<PendingOperation>()
        for (op in store.all()) {
            val next = when (op.state) {
                OpState.SUBMIT_ATTEMPTED -> store.transition(op.id, setOf(OpState.SUBMIT_ATTEMPTED), OpState.OUTCOME_UNKNOWN) {
                    it.copy(reason = "App stopped while sending. The bytes may have reached the cluster; observe the known signature.")
                }
                OpState.REVIEWED -> store.transition(op.id, setOf(OpState.REVIEWED), OpState.ABANDONED) { it.copy(reason = "App restarted before signing. Review again.") }
                OpState.SIGNING -> store.transition(op.id, setOf(OpState.SIGNING), OpState.ABANDONED) {
                    it.copy(reason = "App stopped while the wallet was open. No signed bytes were received or sent.")
                }
                else -> null
            }
            next?.let(changed::add)
        }
        return changed
    }

    fun beginSigning(id: String): Outcome {
        val op = store.transition(id, setOf(OpState.REVIEWED), OpState.SIGNING) { it.copy(approvedAt = now()) }
        return if (op != null) Outcome.Done(op) else Outcome.Busy("This review is not waiting for approval (${store.get(id)?.state ?: "missing"})")
    }

    fun recordSigned(id: String, signature: String, signedTx: ByteArray): PendingOperation? =
        store.transition(id, setOf(OpState.SIGNING), OpState.SIGNED) {
            it.copy(signedAt = now(), signature = signature, signedTxBase64 = java.util.Base64.getEncoder().encodeToString(signedTx), reason = null)
        }

    fun recordRefused(id: String, reason: String): PendingOperation? =
        store.transition(id, setOf(OpState.REVIEWED, OpState.SIGNING), OpState.REFUSED) { it.copy(reason = reason) }

    /** Signed bytes that were never handed to the network can be discarded; the reservation is released. */
    fun discardSigned(id: String): PendingOperation? =
        store.transition(id, setOf(OpState.SIGNED), OpState.ABANDONED) { it.copy(reason = "Signed bytes discarded before sending") }

    suspend fun submit(id: String): Outcome {
        if (!sendMutex.tryLock()) return Outcome.Busy("A submission is already in progress")
        try {
            val op = store.transition(id, setOf(OpState.SIGNED), OpState.SUBMIT_ATTEMPTED) {
                it.copy(submitAttempts = it.submitAttempts + 1, submitAttemptedAt = now())
            } ?: return Outcome.NotAllowed("Only verified, never-sent bytes can be submitted (current: ${store.get(id)?.state ?: "missing"})")
            val bytes = op.signedTransaction ?: return Outcome.NotAllowed("No signed bytes recorded")
            val returned = try {
                gateway.send(bytes)
            } catch (e: CancellationException) {
                store.transition(id, setOf(OpState.SUBMIT_ATTEMPTED), OpState.OUTCOME_UNKNOWN) { it.copy(reason = "Sending was interrupted; outcome unknown") }
                throw e
            } catch (e: Exception) {
                val why = if (e is java.net.SocketTimeoutException) "RPC timed out" else "RPC error: ${e.message ?: e.javaClass.simpleName}"
                return Outcome.Done(store.transition(id, setOf(OpState.SUBMIT_ATTEMPTED), OpState.OUTCOME_UNKNOWN) {
                    it.copy(reason = "$why. The bytes may still land; observe the known signature. Not resent automatically.")
                }!!)
            }
            return if (returned != op.signature) {
                Outcome.Done(store.transition(id, setOf(OpState.SUBMIT_ATTEMPTED), OpState.OUTCOME_UNKNOWN) {
                    it.copy(reason = "RPC reported a different signature; observing the wallet signature instead")
                }!!)
            } else {
                Outcome.Done(store.transition(id, setOf(OpState.SUBMIT_ATTEMPTED), OpState.SUBMITTED) { it.copy(submittedAt = now(), reason = null) }!!)
            }
        } finally {
            sendMutex.unlock()
        }
    }

    /**
     * Reads chain state for one operation. Never sends. Block height is read BEFORE the status so a
     * NOT_FOUND result after a past-expiry height means the transaction can no longer land.
     */
    suspend fun observe(id: String): Outcome {
        if (!observeMutex.tryLock()) return Outcome.Busy("An observation is already in progress")
        try {
            val op = store.get(id) ?: return Outcome.NotAllowed("Unknown operation")
            if (!op.state.needsObservation && op.state != OpState.SIGNED) return Outcome.NotAllowed("Nothing to observe in state ${op.state}")
            val observedAt = now()
            val height = try { gateway.blockHeight() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            if (op.state == OpState.SIGNED) {
                // Never sent: only expiry matters.
                return if (height != null && height > op.lastValidBlockHeight) {
                    Outcome.Done(store.transition(id, setOf(OpState.SIGNED), OpState.EXPIRED) {
                        it.copy(lastObservation = "Block height $height > last valid ${it.lastValidBlockHeight}", lastObservedAt = observedAt, reason = "Blockhash expired before sending. Review again.")
                    } ?: store.get(id)!!)
                } else Outcome.Done(op)
            }
            val sig = op.signature ?: return Outcome.NotAllowed("No signature recorded")
            val status = try { gateway.status(sig) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                val kept = store.transition(id, op.state.let { setOf(it) }, op.state) {
                    it.copy(lastObservation = "Status unavailable: ${e.message ?: e.javaClass.simpleName}", lastObservedAt = observedAt)
                }
                return Outcome.Done(kept ?: store.get(id)!!)
            }
            val from = setOf(OpState.SUBMIT_ATTEMPTED, OpState.SUBMITTED, OpState.OUTCOME_UNKNOWN, OpState.CONFIRMED)
            val next = when (status) {
                ChainStatus.Finalized -> store.transition(id, from, OpState.FINALIZED) { it.copy(lastObservation = "FINALIZED", lastObservedAt = observedAt, reason = null) }
                ChainStatus.Confirmed -> store.transition(id, from, OpState.CONFIRMED) { it.copy(lastObservation = "CONFIRMED", lastObservedAt = observedAt, reason = null) }
                is ChainStatus.Failed -> store.transition(id, from, OpState.FAILED) {
                    it.copy(lastObservation = "FAILED: ${status.error}", lastObservedAt = observedAt, reason = "Cluster reported an error. A network fee may still have been charged.")
                }
                ChainStatus.Processed, ChainStatus.NotFound -> {
                    val label = if (status == ChainStatus.Processed) "PROCESSED (not yet confirmed)" else "NOT_FOUND"
                    if (status == ChainStatus.NotFound && height != null && height > op.lastValidBlockHeight && op.state != OpState.CONFIRMED) {
                        store.transition(id, from, OpState.EXPIRED) {
                            it.copy(lastObservation = "NOT_FOUND at block height $height > last valid ${it.lastValidBlockHeight}", lastObservedAt = observedAt,
                                reason = "Transaction can no longer land. Review again to create a new message.")
                        }
                    } else {
                        store.transition(id, setOf(op.state), op.state) {
                            it.copy(lastObservation = label + (height?.let { h -> " · block height $h, last valid ${it.lastValidBlockHeight}" } ?: " · block height unavailable"), lastObservedAt = observedAt)
                        }
                    }
                }
            }
            return Outcome.Done(next ?: store.get(id)!!)
        } finally {
            observeMutex.unlock()
        }
    }

    /** Observes every operation whose outcome is not settled. Reads only. */
    suspend fun reconcileAll(): List<PendingOperation> =
        store.all().filter { it.state.needsObservation || it.state == OpState.SIGNED }.mapNotNull { (observe(it.id) as? Outcome.Done)?.op }
}
