package com.edgeore.app.ui

import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Format {
    fun sol(lamports: Long): String = BigDecimal(lamports).movePointLeft(9).stripTrailingZeros().toPlainString() + " SOL"
    fun short(s: String, keep: Int = 4): String = if (s.length <= keep * 2 + 1) s else s.take(keep) + "…" + s.takeLast(keep)
    private val local = DateTimeFormatter.ofPattern("d MMM, HH:mm:ss").withZone(ZoneId.systemDefault())
    fun time(iso: String): String = runCatching { local.format(Instant.parse(iso)) }.getOrDefault(iso)
    fun time(ms: Long): String = local.format(Instant.ofEpochMilli(ms))
    /** Decimal units (1 GB = 10^9 bytes), matching how Hugging Face lists file sizes. Exact bytes are shown alongside. */
    fun bytes(n: Long): String = when {
        n >= 1_000_000_000L -> BigDecimal(n).movePointLeft(9).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + " GB"
        n >= 1_000_000L -> BigDecimal(n).movePointLeft(6).setScale(1, java.math.RoundingMode.HALF_UP).toPlainString() + " MB"
        else -> "$n bytes"
    }
    fun ago(ms: Long, now: Long = System.currentTimeMillis()): String {
        val s = (now - ms) / 1000
        return when { s < 60 -> "${s}s ago"; s < 3600 -> "${s / 60} min ago"; else -> "${s / 3600} h ago" }
    }
}
