package com.edgeore.app.device

import java.util.Locale

/** Phone storage and app network readings. Real platform counters only; unsupported readings are null, never zero. */
data class StorageReading(
    val freeBytes: Long,
    val totalBytes: Long,
)

enum class NetActivity { IDLE, ACTIVE, UNAVAILABLE }

object Telemetry {
    @androidx.annotation.VisibleForTesting
    @Volatile internal var testStorageSource: ((java.io.File) -> StorageReading?)? = null

    @androidx.annotation.VisibleForTesting
    @Volatile internal var testTrafficSource: (() -> Long?)? = null

    /** Decimal gigabytes with two places, e.g. "250.00 GB" (1 GB = 10^9 bytes, as Android Settings shows storage). */
    fun gb(bytes: Long): String = String.format(Locale.US, "%.2f GB", bytes / 1e9)

    fun storage(path: java.io.File): StorageReading? {
        testStorageSource?.let { return it(path) }
        return runCatching {
            val st = android.os.StatFs(path.path)
            StorageReading(st.availableBytes, st.totalBytes).takeIf { it.totalBytes > 0 }
        }.getOrNull()
    }

    /** This app's received + sent bytes (TrafficStats per-UID counters), or null when the platform does not report them. */
    fun appTrafficBytes(): Long? {
        testTrafficSource?.let { return it() }
        val uid = android.os.Process.myUid()
        val rx = android.net.TrafficStats.getUidRxBytes(uid)
        val tx = android.net.TrafficStats.getUidTxBytes(uid)
        return if (rx < 0 || tx < 0) null else rx + tx
    }

    fun activity(
        previous: Long?,
        current: Long?,
    ): NetActivity =
        when {
            previous == null || current == null -> NetActivity.UNAVAILABLE
            current > previous -> NetActivity.ACTIVE
            else -> NetActivity.IDLE
        }
}
