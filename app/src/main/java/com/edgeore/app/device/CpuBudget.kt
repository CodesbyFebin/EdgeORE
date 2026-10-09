package com.edgeore.app.device

/** A stored preference. This build does not apply it to the device or to a workload. */
object CpuBudget {
    const val DEVICE_WIDE_CAP_ENFORCED = false

    fun storedBudgetLabel(percent: Int): String =
        "Stored workload budget $percent%. Not a device-wide CPU cap. Not enforced in this build."

    /** Bytes per second between two monotonic samples. A missing sample or a reset is null, not zero. */
    fun sampleRate(first: Long?, second: Long?, elapsedMillis: Long): Long? {
        if (first == null || second == null || elapsedMillis <= 0 || second < first) return null
        return (second - first) * 1000 / elapsedMillis
    }
}
