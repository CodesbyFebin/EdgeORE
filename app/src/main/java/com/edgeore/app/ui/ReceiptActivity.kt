package com.edgeore.app.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Receipts recorded per local calendar day, for the Receipts activity chart. Built only from receipts stored on this
 * phone; it says nothing about payment, work or anything on chain.
 */
object ReceiptActivity {
    data class Series(val days: List<LocalDate>, val counts: List<Int>, val unreadable: Int) {
        val total: Int get() = counts.sum()
        val isEmpty: Boolean get() = total == 0

        /** Spoken summary for screen readers; also the visible caption. */
        fun summary(): String =
            if (isEmpty) "No receipts in the last ${days.size} days."
            else "$total receipt${if (total == 1) "" else "s"} in the last ${days.size} days, most on one day: ${counts.max()}."
    }

    /**
     * @param createdAt ISO-8601 instants of stored receipts. Values that do not parse are counted in [Series.unreadable]
     * and left out of the chart rather than guessed.
     */
    fun lastDays(createdAt: List<String>, days: Int = 14, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Series {
        require(days >= 2) { "need at least two days" }
        val range = (days - 1 downTo 0).map { today.minusDays(it.toLong()) }
        val index = range.withIndex().associate { it.value to it.index }
        val counts = IntArray(days)
        var unreadable = 0
        createdAt.forEach { iso ->
            val day = runCatching { Instant.parse(iso).atZone(zone).toLocalDate() }.getOrNull()
            if (day == null) unreadable++ else index[day]?.let { counts[it]++ }
        }
        return Series(range, counts.toList(), unreadable)
    }
}
