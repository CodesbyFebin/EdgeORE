package com.edgeore.app.device

import java.io.File

/** One line of /proc/stat. Idle includes iowait when present. */
data class CpuSample(val idle: Long, val total: Long) {
    companion object {
        fun read(): CpuSample? = parse(runCatching { File("/proc/stat").bufferedReader().use { it.readLine() } }.getOrNull())

        fun parse(line: String?): CpuSample? {
            if (line == null || !line.startsWith("cpu ")) return null
            val parts = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
            if (parts.size < 4) return null
            val idle = parts[3] + (parts.getOrNull(4) ?: 0L)
            return CpuSample(idle, parts.sum())
        }

        /** Busy percent between two samples. Null when the clock did not move. */
        fun percent(prev: CpuSample, next: CpuSample): Int? {
            val dt = next.total - prev.total
            if (dt <= 0) return null
            val busy = dt - (next.idle - prev.idle)
            return (busy * 100 / dt).toInt().coerceIn(0, 100)
        }
    }
}

/** Sum of whole-disk sectors from /proc/diskstats, in bytes. Partitions are skipped so they are not counted twice. */
data class DiskSample(val bytes: Long) {
    companion object {
        fun read(): DiskSample? = parse(runCatching { File("/proc/diskstats").readText() }.getOrNull())

        fun parse(text: String?): DiskSample? {
            if (text.isNullOrBlank()) return null
            var sectors = 0L
            var found = false
            for (line in text.lineSequence()) {
                val p = line.trim().split(Regex("\\s+"))
                if (p.size < 10) continue
                val name = p[2]
                if (!wholeDisk(name)) continue
                val read = p[5].toLongOrNull() ?: continue
                val write = p[9].toLongOrNull() ?: continue
                sectors += read + write
                found = true
            }
            return if (found) DiskSample(sectors * 512) else null
        }

        fun wholeDisk(name: String): Boolean = name.matches(Regex("mmcblk\\d+")) ||
            name.matches(Regex("nvme\\d+n\\d+")) ||
            name.matches(Regex("sd[a-z]+")) ||
            name.matches(Regex("vd[a-z]+")) ||
            name.matches(Regex("xvd[a-z]+"))
    }
}

fun ioBytesPerSecond(previous: DiskSample, next: DiskSample, elapsedMs: Long): Long? {
    if (elapsedMs <= 0 || next.bytes < previous.bytes) return null // reset or reboot: unavailable, not zero
    return (next.bytes - previous.bytes) * 1000 / elapsedMs
}

/**
 * Disk throughput from consecutive valid samples only. Each counter is kept together with the
 * monotonic time of that exact sample. A missing sample resets continuity, so a later valid sample
 * never spans an interval the timestamps do not describe. A counter that goes backwards (reboot,
 * device change) is unavailable, not zero.
 */
class DiskRateTracker {
    private var last: DiskSample? = null
    private var lastAtMs: Long = 0L

    @Synchronized
    fun observe(sample: DiskSample?, monotonicMs: Long): Long? {
        if (sample == null) { last = null; return null }
        val prev = last
        val prevAt = lastAtMs
        last = sample
        lastAtMs = monotonicMs
        if (prev == null) return null
        val elapsed = monotonicMs - prevAt
        if (elapsed <= 0 || sample.bytes < prev.bytes) return null
        return (sample.bytes - prev.bytes) * 1000 / elapsed
    }
}

/** CPU busy percent across consecutive valid samples; a missing sample resets continuity. */
class CpuRateTracker {
    private var last: CpuSample? = null

    @Synchronized
    fun observe(sample: CpuSample?): Int? {
        if (sample == null) { last = null; return null }
        val prev = last
        last = sample
        if (prev == null || sample.total < prev.total) return null
        return CpuSample.percent(prev, sample)
    }
}
