package com.edgeore.app.solana

import com.edgeore.app.io.AtomicFiles
import com.edgeore.app.io.BoundedInput
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID

/**
 * Lifecycle of one reviewed wallet action. The names are deliberately narrow:
 * - REVIEWED: exact message shown and budget reserved; no wallet signature exists.
 * - SIGNING: a wallet signature was requested; no bytes received yet.
 * - SIGNED: wallet returned bytes that matched the reviewed message and verified. Never sent.
 * - SUBMIT_ATTEMPTED: durable intent to send these exact bytes, written BEFORE the network call.
 * - SUBMITTED: the RPC accepted the bytes and returned the same signature. Not a confirmation.
 * - OUTCOME_UNKNOWN: the bytes may or may not have reached the cluster (timeout, crash, RPC error).
 * - CONFIRMED / FINALIZED: observed via getSignatureStatuses.
 * - FAILED: the cluster reported a transaction error for this signature.
 * - EXPIRED: bytes that were NEVER handed to the network (unsigned review, signing session, or signed-but-unsent)
 *   whose blockhash is past lastValidBlockHeight. Nothing was sent, so the reservation is released.
 *   Submitted or uncertain operations never become EXPIRED: after expiry a NOT_FOUND status keeps them
 *   OUTCOME_UNKNOWN with their reservation (see docs/qualification-status.md, "Expiry policy").
 * - REFUSED: refused before any bytes were sent (by you, the wallet, or the exact-message check).
 * - ABANDONED: the draft or signing session ended without bytes being sent.
 */
enum class OpState {
    REVIEWED, SIGNING, SIGNED, SUBMIT_ATTEMPTED, SUBMITTED, OUTCOME_UNKNOWN, CONFIRMED, FINALIZED, FAILED, EXPIRED, REFUSED, ABANDONED;

    /** Principal is counted against the daily budget in these states (each operation exactly once). */
    val holdsBudget: Boolean get() = this !in setOf(FAILED, EXPIRED, REFUSED, ABANDONED)
    val terminal: Boolean get() = this in setOf(FINALIZED, FAILED, EXPIRED, REFUSED, ABANDONED)
    /** Bytes may have left the app; only chain observation can settle these. */
    val needsObservation: Boolean get() = this in setOf(SUBMIT_ATTEMPTED, SUBMITTED, OUTCOME_UNKNOWN, CONFIRMED)
}

data class PendingOperation(
    val id: String,
    val schemaVersion: Int = SCHEMA_VERSION,
    val cluster: String,
    val signer: String,
    val recipient: String,
    val lamports: Long,
    val messageSha256: String,
    val messageBase64: String,
    val blockhash: String,
    val lastValidBlockHeight: Long,
    val budgetDay: String,
    val reviewedAt: String,
    val approvedAt: String? = null,
    val signedAt: String? = null,
    val signature: String? = null,
    val signedTxBase64: String? = null,
    val submitAttempts: Int = 0,
    val submitAttemptedAt: String? = null,
    val submittedAt: String? = null,
    val lastObservation: String? = null,
    val lastObservedAt: String? = null,
    val state: OpState = OpState.REVIEWED,
    val reason: String? = null,
    val receiptedState: OpState? = null,
    val updatedAt: String = reviewedAt,
) {
    val message: ByteArray get() = Base64.getDecoder().decode(messageBase64)
    val signedTransaction: ByteArray? get() = signedTxBase64?.let { Base64.getDecoder().decode(it) }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("schemaVersion", schemaVersion).put("cluster", cluster).put("signer", signer)
        .put("recipient", recipient).put("lamports", lamports).put("messageSha256", messageSha256)
        .put("messageBase64", messageBase64).put("blockhash", blockhash).put("lastValidBlockHeight", lastValidBlockHeight)
        .put("budgetDay", budgetDay).put("reviewedAt", reviewedAt).put("approvedAt", approvedAt ?: JSONObject.NULL)
        .put("signedAt", signedAt ?: JSONObject.NULL).put("signature", signature ?: JSONObject.NULL)
        .put("signedTxBase64", signedTxBase64 ?: JSONObject.NULL).put("submitAttempts", submitAttempts)
        .put("submitAttemptedAt", submitAttemptedAt ?: JSONObject.NULL).put("submittedAt", submittedAt ?: JSONObject.NULL)
        .put("lastObservation", lastObservation ?: JSONObject.NULL).put("lastObservedAt", lastObservedAt ?: JSONObject.NULL)
        .put("state", state.name).put("reason", reason ?: JSONObject.NULL)
        .put("receiptedState", receiptedState?.name ?: JSONObject.NULL).put("updatedAt", updatedAt)

    companion object {
        const val SCHEMA_VERSION = 1
        private fun JSONObject.str(k: String): String? = if (isNull(k) || !has(k)) null else getString(k)
        fun fromJson(o: JSONObject): PendingOperation {
            val v = o.getInt("schemaVersion")
            require(v == SCHEMA_VERSION) { "Unsupported operation schema $v" }
            return PendingOperation(
                id = o.getString("id"), schemaVersion = v, cluster = o.getString("cluster"), signer = o.getString("signer"),
                recipient = o.getString("recipient"), lamports = o.getLong("lamports"), messageSha256 = o.getString("messageSha256"),
                messageBase64 = o.getString("messageBase64"), blockhash = o.getString("blockhash"),
                lastValidBlockHeight = o.getLong("lastValidBlockHeight"), budgetDay = o.getString("budgetDay"),
                reviewedAt = o.getString("reviewedAt"), approvedAt = o.str("approvedAt"), signedAt = o.str("signedAt"),
                signature = o.str("signature"), signedTxBase64 = o.str("signedTxBase64"), submitAttempts = o.getInt("submitAttempts"),
                submitAttemptedAt = o.str("submitAttemptedAt"), submittedAt = o.str("submittedAt"),
                lastObservation = o.str("lastObservation"), lastObservedAt = o.str("lastObservedAt"),
                state = OpState.valueOf(o.getString("state")), reason = o.str("reason"),
                receiptedState = o.str("receiptedState")?.let(OpState::valueOf), updatedAt = o.getString("updatedAt"),
            )
        }
    }
}

class OperationStoreCorrupt(message: String) : Exception(message)

/**
 * A write to the operation store failed. Memory still equals the last durable state, and the store is
 * latched unavailable: every later financial write is refused until the app restarts and reloads disk.
 */
class OperationStoreUnavailable(message: String) : Exception(message)

/**
 * Durable operation repository. Every transition is a compare-and-set on the current state and is
 * fsynced (temp file + rename) before the method returns, so a caller that performs network I/O
 * after a transition knows the transition survives process death.
 *
 * The daily budget is per signer and cluster, per UTC calendar day of review (documented policy).
 */
class OperationStore(
    private val file: File,
    private val clock: () -> Instant = Instant::now,
    /** Durable write of the whole store. Injectable so tests can fail persistence at any transition. */
    private val writer: (File, ByteArray) -> Unit = { f, b -> AtomicFiles.write(f, b) },
) {
    private val lock = Any()
    private var ops = LinkedHashMap<String, PendingOperation>()
    @Volatile private var failure: String? = null

    /** Non-null once a write failed. Financial actions must be refused while this is set. */
    val unavailableReason: String? get() = failure

    init { load() }

    private fun load() {
        if (!file.exists()) return
        val bytes = FileInputStream(file).use { BoundedInput.readAtMost(it, MAX_FILE_BYTES) }
        val doc = try { JSONObject(String(bytes, Charsets.UTF_8)) } catch (e: Exception) {
            // Never treat a damaged store as "no pending operations": that would free budget and hide unknown outcomes.
            throw OperationStoreCorrupt("Operation store is unreadable; refusing to start with an empty state")
        }
        if (doc.optInt("schema") != STORE_SCHEMA) throw OperationStoreCorrupt("Unsupported operation store schema")
        val arr = doc.getJSONArray("operations")
        for (i in 0 until arr.length()) PendingOperation.fromJson(arr.getJSONObject(i)).let { ops[it.id] = it }
    }

    private fun ensureWritable() {
        failure?.let { throw OperationStoreUnavailable(it) }
    }

    /**
     * Writes [candidate] durably, then publishes it in memory. If the write fails nothing in memory
     * changes (memory == disk), the store latches unavailable, and the caller gets a typed exception.
     */
    private fun commit(candidate: LinkedHashMap<String, PendingOperation>) {
        ensureWritable()
        val arr = JSONArray().also { a -> candidate.values.forEach { a.put(it.toJson()) } }
        try {
            writer(file, JSONObject().put("schema", STORE_SCHEMA).put("operations", arr).toString().toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            val why = "Operation storage write failed (${e.message ?: e.javaClass.simpleName}). Wallet actions are disabled until EdgeORE restarts; nothing further will be signed or sent."
            failure = why
            throw OperationStoreUnavailable(why)
        }
        ops = candidate
    }

    fun all(): List<PendingOperation> = synchronized(lock) { ops.values.toList() }
    fun get(id: String): PendingOperation? = synchronized(lock) { ops[id] }

    fun budgetDay(at: Instant = clock()): String = at.atZone(ZoneOffset.UTC).toLocalDate().toString()

    /** Lamports counted today for this signer and cluster. Each operation is counted once, whatever its state. */
    fun exposure(signer: String, cluster: String, day: String = budgetDay()): Long = synchronized(lock) {
        ops.values.filter { it.signer == signer && it.cluster == cluster && it.budgetDay == day && it.state.holdsBudget }.sumOf { it.lamports }
    }

    sealed interface ReserveResult {
        data class Reserved(val op: PendingOperation) : ReserveResult
        data class Refused(val reason: String) : ReserveResult
    }

    /**
     * Records a reviewed message and reserves its principal. Any earlier REVIEWED draft for the same
     * signer/cluster is abandoned first (a new review replaces an unsigned one, so its reservation is freed).
     */
    fun reserve(
        cluster: String, signer: String, recipient: String, lamports: Long, message: ByteArray, messageSha256: String,
        blockhash: String, lastValidBlockHeight: Long, dailyLimit: Long, externalLamports: Long = 0,
    ): ReserveResult = synchronized(lock) {
        failure?.let { return ReserveResult.Refused(it) }
        if (lamports <= 0) return ReserveResult.Refused("Amount must be greater than zero")
        if (dailyLimit < 0) return ReserveResult.Refused("Daily limit is invalid")
        if (externalLamports < 0) return ReserveResult.Refused("Budget inputs are invalid")
        val now = clock().toString()
        val day = budgetDay()
        val candidate = LinkedHashMap(ops)
        val abandoned = candidate.values.filter { it.signer == signer && it.cluster == cluster && it.state == OpState.REVIEWED }
        abandoned.forEach { candidate[it.id] = it.copy(state = OpState.ABANDONED, reason = "Replaced by a new review", updatedAt = now) }
        val current = candidate.values.filter { it.signer == signer && it.cluster == cluster && it.budgetDay == day && it.state.holdsBudget }.sumOf { it.lamports } + externalLamports
        val refusal = try {
            if (Math.addExact(current, lamports) > dailyLimit) "Daily budget would be exceeded: ${current} lamports already counted today for this account" else null
        } catch (_: ArithmeticException) { "Budget arithmetic overflow" }
        try {
            if (refusal != null) {
                if (abandoned.isNotEmpty()) commit(candidate)
                return ReserveResult.Refused(refusal)
            }
            val op = PendingOperation(
                id = UUID.randomUUID().toString(), cluster = cluster, signer = signer, recipient = recipient, lamports = lamports,
                messageSha256 = messageSha256, messageBase64 = Base64.getEncoder().encodeToString(message), blockhash = blockhash,
                lastValidBlockHeight = lastValidBlockHeight, budgetDay = day, reviewedAt = now,
            )
            candidate[op.id] = op
            commit(candidate)
            ReserveResult.Reserved(op)
        } catch (e: OperationStoreUnavailable) {
            ReserveResult.Refused(e.message!!)
        }
    }

    /**
     * Compare-and-set transition. Returns the updated operation, or null when the current state is not in [from].
     * Throws [OperationStoreUnavailable] if the new state could not be made durable; memory is then unchanged.
     */
    fun transition(id: String, from: Set<OpState>, to: OpState, mutate: (PendingOperation) -> PendingOperation = { it }): PendingOperation? = synchronized(lock) {
        ensureWritable()
        val cur = ops[id] ?: return null
        if (cur.state !in from) return null
        val next = mutate(cur).copy(state = to, updatedAt = clock().toString())
        commit(LinkedHashMap(ops).also { it[id] = next })
        next
    }

    fun markReceipted(id: String, state: OpState): Unit = synchronized(lock) {
        val cur = ops[id] ?: return
        if (cur.receiptedState == state) return
        commit(LinkedHashMap(ops).also { it[id] = cur.copy(receiptedState = state) })
    }

    /** Operations whose latest durable state has no receipt yet (e.g. the process died before the append). */
    fun unreceipted(): List<PendingOperation> = synchronized(lock) { ops.values.filter { it.receiptedState != it.state } }

    companion object {
        const val STORE_SCHEMA = 1
        const val MAX_FILE_BYTES = 4L * 1024 * 1024
    }
}
