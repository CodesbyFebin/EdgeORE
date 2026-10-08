package com.edgeore.app.solana

/**
 * In-memory daily exposure for one cluster and signer. A timed-out submit stays reserved.
 * Settled rows are excluded from [pending] because a submitted receipt already counts as spent.
 */
class SpendLedger {
    private val lock = Any()
    private val rows = LinkedHashMap<String, Row>()

    enum class State { RESERVED, UNKNOWN, SETTLED, RELEASED }

    data class Row(
        val operationId: String,
        val lamports: Long,
        val cluster: String,
        val signer: String,
        val messageSha256: String,
        val state: State,
    )

    sealed interface ReserveResult {
        data class Reserved(val row: Row) : ReserveResult
        data class Refused(val reason: String) : ReserveResult
    }

    fun pending(): Long = synchronized(lock) {
        rows.values.filter { it.state == State.RESERVED || it.state == State.UNKNOWN }.sumOf { it.lamports }
    }

    fun reserve(
        operationId: String,
        lamports: Long,
        cluster: String,
        signer: String,
        messageSha256: String,
        alreadySpent: Long,
        dailyLimit: Long,
    ): ReserveResult = synchronized(lock) {
        if (operationId.isBlank()) return ReserveResult.Refused("Operation id is blank")
        if (lamports <= 0) return ReserveResult.Refused("Amount must be greater than zero")
        if (alreadySpent < 0 || dailyLimit < 0) return ReserveResult.Refused("Budget inputs are invalid")
        val existing = rows[operationId]
        if (existing != null && existing.state != State.RELEASED) {
            if (existing.lamports == lamports && existing.messageSha256 == messageSha256 && existing.state == State.RESERVED) {
                return ReserveResult.Reserved(existing)
            }
            return ReserveResult.Refused("Operation id is already in use")
        }
        val next = try {
            Math.addExact(alreadySpent, Math.addExact(pendingUnlocked(), lamports))
        } catch (_: ArithmeticException) {
            return ReserveResult.Refused("Budget arithmetic overflow")
        }
        if (next > dailyLimit) return ReserveResult.Refused("Daily budget would be exceeded")
        val row = Row(operationId, lamports, cluster, signer, messageSha256, State.RESERVED)
        rows[operationId] = row
        ReserveResult.Reserved(row)
    }

    fun markUnknown(operationId: String): Boolean = synchronized(lock) {
        val row = rows[operationId] ?: return false
        if (row.state != State.RESERVED && row.state != State.UNKNOWN) return false
        rows[operationId] = row.copy(state = State.UNKNOWN)
        true
    }

    fun settle(operationId: String): Boolean = synchronized(lock) {
        val row = rows[operationId] ?: return false
        if (row.state != State.RESERVED && row.state != State.UNKNOWN) return false
        rows[operationId] = row.copy(state = State.SETTLED)
        true
    }

    fun release(operationId: String): Boolean = synchronized(lock) {
        val row = rows[operationId] ?: return false
        if (row.state != State.RESERVED) return false
        rows[operationId] = row.copy(state = State.RELEASED)
        true
    }

    private fun pendingUnlocked(): Long =
        rows.values.filter { it.state == State.RESERVED || it.state == State.UNKNOWN }.sumOf { it.lamports }
}
