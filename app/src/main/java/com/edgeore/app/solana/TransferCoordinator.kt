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
    /** The RPC answered, but not in a shape that settles anything. Treated like an unavailable read. */
    data class Unavailable(val reason: String) : ChainStatus
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
 * - an expired operation needs a fresh review: this class never builds a message;
 * - only never-sent bytes can become EXPIRED; once bytes may have left, NOT_FOUND after expiry stays uncertain;
 * - if the operation store cannot persist a transition, the step is refused (and nothing is sent).
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

    /** Runs a store write; a failed write becomes a typed refusal instead of an exception. */
    private inline fun <T> stored(block: () -> T): Result<T> = try { Result.success(block()) } catch (e: OperationStoreUnavailable) { Result.failure(e) }

    /** Signing needs at least this many blocks before lastValidBlockHeight (about 8 s at 400 ms/block). */
    val signingMarginBlocks: Long = SIGNING_MARGIN_BLOCKS

    companion object { const val SIGNING_MARGIN_BLOCKS = 20L }

    /** Called once at start-up, before any UI action. No network. */
    fun recoverAfterRestart(): List<PendingOperation> {
        val changed = mutableListOf<PendingOperation>()
        if (store.unavailableReason != null) return changed
        for (op in store.all()) {
            val next = stored { when (op.state) {
                OpState.SUBMIT_ATTEMPTED -> store.transition(op.id, setOf(OpState.SUBMIT_ATTEMPTED), OpState.OUTCOME_UNKNOWN) {
                    it.copy(reason = "App stopped while sending. The bytes may have reached the cluster; observe the known signature.")
                }
                OpState.REVIEWED -> store.transition(op.id, setOf(OpState.REVIEWED), OpState.ABANDONED) { it.copy(reason = "App restarted before signing. Review again.") }
                OpState.SIGNING -> store.transition(op.id, setOf(OpState.SIGNING), OpState.ABANDONED) {
                    it.copy(reason = "App stopped while the wallet was open. No signed bytes were received or sent.")
                }
                else -> null
            } }.getOrNull()
            next?.let(changed::add)
            if (store.unavailableReason != null) break
        }
        return changed
    }

    fun beginSigning(id: String): Outcome {
        val op = stored { store.transition(id, setOf(OpState.REVIEWED), OpState.SIGNING) { it.copy(approvedAt = now()) } }
            .getOrElse { return Outcome.NotAllowed(it.message!!) }
        return if (op != null) Outcome.Done(op) else Outcome.Busy("This review is not waiting for approval (${store.get(id)?.state ?: "missing"})")
    }

    /**
     * Single-flight REVIEWED -> SIGNING, then a fresh block-height read BEFORE the wallet opens.
     * Height unavailable: refused (validity cannot be shown). Past or within [signingMarginBlocks] of
     * lastValidBlockHeight: EXPIRED (nothing was signed or sent; reservation released) and a fresh
     * review is required. The reviewed message is never rebuilt or mutated here.
     */
    suspend fun beginSigningChecked(id: String): Outcome {
        val begun = beginSigning(id)
        if (begun !is Outcome.Done) return begun
        val op = begun.op
        val height = try { gateway.blockHeight() } catch (e: CancellationException) {
            stored { recordRefusedOrThrow(id, "Signing was interrupted before the wallet opened") }; throw e
        } catch (e: Exception) { null }
        if (height == null) {
            val reason = "Block height unavailable, so the blockhash cannot be shown to be valid. Not signed; try again."
            stored { recordRefusedOrThrow(id, reason) }.onFailure { return Outcome.NotAllowed(it.message!!) }
            return Outcome.NotAllowed(reason)
        }
        if (height > op.lastValidBlockHeight - signingMarginBlocks) {
            val reason = "Blockhash expired or about to expire (block height $height, last valid ${op.lastValidBlockHeight}). Nothing was signed or sent. Review again to build a new message."
            stored {
                store.transition(id, setOf(OpState.SIGNING), OpState.EXPIRED) {
                    it.copy(reason = reason, lastObservation = "Block height $height checked before signing", lastObservedAt = now())
                }
            }.onFailure { return Outcome.NotAllowed(it.message!!) }
            return Outcome.NotAllowed(reason)
        }
        return Outcome.Done(op)
    }

    private fun recordRefusedOrThrow(id: String, reason: String) =
        store.transition(id, setOf(OpState.REVIEWED, OpState.SIGNING), OpState.REFUSED) { it.copy(reason = reason) }

    fun recordSigned(id: String, signature: String, signedTx: ByteArray): PendingOperation? = stored {
        store.transition(id, setOf(OpState.SIGNING), OpState.SIGNED) {
            it.copy(signedAt = now(), signature = signature, signedTxBase64 = java.util.Base64.getEncoder().encodeToString(signedTx), reason = null)
        }
    }.getOrNull()

    fun recordRefused(id: String, reason: String): PendingOperation? = stored { recordRefusedOrThrow(id, reason) }.getOrNull()

    /** Signed bytes that were never handed to the network can be discarded; the reservation is released. */
    fun discardSigned(id: String): PendingOperation? = stored {
        store.transition(id, setOf(OpState.SIGNED), OpState.ABANDONED) { it.copy(reason = "Signed bytes discarded before sending") }
    }.getOrNull()

    suspend fun submit(id: String): Outcome {
        if (!sendMutex.tryLock()) return Outcome.Busy("A submission is already in progress")
        try {
            // The attempt must be durable before any byte leaves; if it cannot be stored, nothing is sent.
            val op = stored {
                store.transition(id, setOf(OpState.SIGNED), OpState.SUBMIT_ATTEMPTED) {
                    it.copy(submitAttempts = it.submitAttempts + 1, submitAttemptedAt = now())
                }
            }.getOrElse { return Outcome.NotAllowed(it.message!! + " Not sent.") }
                ?: return Outcome.NotAllowed("Only verified, never-sent bytes can be submitted (current: ${store.get(id)?.state ?: "missing"})")
            val bytes = op.signedTransaction ?: return Outcome.NotAllowed("No signed bytes recorded")
            val returned = try {
                gateway.send(bytes)
            } catch (e: CancellationException) {
                stored { store.transition(id, setOf(OpState.SUBMIT_ATTEMPTED), OpState.OUTCOME_UNKNOWN) { it.copy(reason = "Sending was interrupted; outcome unknown") } }
                throw e
            } catch (e: Exception) {
                val why = if (e is java.net.SocketTimeoutException) "RPC timed out" else "RPC error: ${e.message ?: e.javaClass.simpleName}"
                return afterSend(id) {
                    store.transition(id, setOf(OpState.SUBMIT_ATTEMPTED), OpState.OUTCOME_UNKNOWN) {
                        it.copy(reason = "$why. The bytes may still land; observe the known signature. Not resent automatically.")
                    }
                }
            }
            return afterSend(id) {
                if (returned != op.signature) store.transition(id, setOf(OpState.SUBMIT_ATTEMPTED), OpState.OUTCOME_UNKNOWN) {
                    it.copy(reason = "RPC reported a different signature; observing the wallet signature instead")
                } else store.transition(id, setOf(OpState.SUBMIT_ATTEMPTED), OpState.SUBMITTED) { it.copy(submittedAt = now(), reason = null) }
            }
        } finally {
            sendMutex.unlock()
        }
    }

    /**
     * Records the result of a send. If that write fails, the durable state stays SUBMIT_ATTEMPTED
     * (memory == disk), which restart recovery turns into OUTCOME_UNKNOWN: uncertainty is preserved.
     */
    private inline fun afterSend(id: String, write: () -> PendingOperation?): Outcome =
        stored(write).fold({ Outcome.Done(it ?: store.get(id)!!) }, {
            Outcome.NotAllowed("The bytes were handed to the RPC, but the result could not be stored. Outcome unknown. " + it.message)
        })

    /**
     * Reads chain state for one operation. Never sends. Block height is read BEFORE the status. Never-sent SIGNED bytes
     * past expiry become EXPIRED (released). A sent or uncertain operation that is NOT_FOUND past expiry stays
     * OUTCOME_UNKNOWN with its reservation: one RPC's NOT_FOUND is strong but not conclusive evidence.
     */
    suspend fun observe(id: String): Outcome {
        if (!observeMutex.tryLock()) return Outcome.Busy("An observation is already in progress")
        try {
            return stored { observeLocked(id) }.getOrElse { Outcome.NotAllowed(it.message!!) }
        } finally {
            observeMutex.unlock()
        }
    }

    private suspend fun observeLocked(id: String): Outcome {
        val op = store.get(id) ?: return Outcome.NotAllowed("Unknown operation")
        if (!op.state.needsObservation && op.state != OpState.SIGNED) return Outcome.NotAllowed("Nothing to observe in state ${op.state}")
        val observedAt = now()
        val height = try { gateway.blockHeight() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        if (op.state == OpState.SIGNED) {
            // Never handed to the network: expiry makes these bytes harmless, so the reservation can be released.
            return if (height != null && height > op.lastValidBlockHeight) {
                Outcome.Done(store.transition(id, setOf(OpState.SIGNED), OpState.EXPIRED) {
                    it.copy(lastObservation = "Block height $height > last valid ${it.lastValidBlockHeight}", lastObservedAt = observedAt,
                        reason = "Signed bytes were never sent and their blockhash has expired, so they cannot be used. Reservation released. Review again for a new transfer.")
                } ?: store.get(id)!!)
            } else Outcome.Done(op)
        }
        val sig = op.signature ?: return Outcome.NotAllowed("No signature recorded")
        val status = try { gateway.status(sig) } catch (e: CancellationException) { throw e } catch (e: Exception) {
            ChainStatus.Unavailable("Status unavailable: ${e.message ?: e.javaClass.simpleName}")
        }
        val from = setOf(OpState.SUBMIT_ATTEMPTED, OpState.SUBMITTED, OpState.OUTCOME_UNKNOWN, OpState.CONFIRMED)
        val next = when (status) {
            ChainStatus.Finalized -> store.transition(id, from, OpState.FINALIZED) { it.copy(lastObservation = "FINALIZED", lastObservedAt = observedAt, reason = null) }
            ChainStatus.Confirmed -> store.transition(id, from, OpState.CONFIRMED) { it.copy(lastObservation = "CONFIRMED", lastObservedAt = observedAt, reason = null) }
            is ChainStatus.Failed -> store.transition(id, from, OpState.FAILED) {
                it.copy(lastObservation = "FAILED: ${status.error}", lastObservedAt = observedAt, reason = "Cluster reported an error. A network fee may still have been charged.")
            }
            is ChainStatus.Unavailable -> store.transition(id, setOf(op.state), op.state) {
                it.copy(lastObservation = status.reason.let { r -> if (r.startsWith("Status unavailable")) r else "Status unavailable: $r" }, lastObservedAt = observedAt)
            }
            ChainStatus.Processed, ChainStatus.NotFound -> {
                val label = if (status == ChainStatus.Processed) "PROCESSED (not yet confirmed)" else "NOT_FOUND"
                val pastExpiry = height != null && height > op.lastValidBlockHeight
                if (status == ChainStatus.NotFound && pastExpiry && op.state != OpState.CONFIRMED) {
                    // Bytes may have left the app. Not finding them after expiry is not proof they never landed,
                    // so the operation stays uncertain and keeps its reservation. No resend, no "safe to retry".
                    store.transition(id, setOf(OpState.SUBMIT_ATTEMPTED, OpState.SUBMITTED, OpState.OUTCOME_UNKNOWN), OpState.OUTCOME_UNKNOWN) {
                        it.copy(lastObservation = "NOT_FOUND at block height $height > last valid ${it.lastValidBlockHeight}", lastObservedAt = observedAt,
                            reason = "Not found by the RPC after the blockhash expired. That is not proof it never landed, so this stays uncertain and its amount stays counted. Keep observing or check an explorer before sending again.")
                    }
                } else {
                    store.transition(id, setOf(op.state), op.state) {
                        it.copy(lastObservation = label + (height?.let { h -> " · block height $h, last valid ${it.lastValidBlockHeight}" } ?: " · block height unavailable"), lastObservedAt = observedAt)
                    }
                }
            }
        }
        return Outcome.Done(next ?: store.get(id)!!)
    }

    /** Observes every operation whose outcome is not settled. Reads only. */
    suspend fun reconcileAll(): List<PendingOperation> =
        store.all().filter { it.state.needsObservation || it.state == OpState.SIGNED }.mapNotNull { (observe(it.id) as? Outcome.Done)?.op }
}
