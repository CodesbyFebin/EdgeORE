package com.edgeore.app.device

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.StatFs
import kotlin.math.abs

/** Readings taken from this device. Null means the source did not answer. */
data class DeviceResources(
    val storageTotal: Long?,
    val storageFree: Long?,
    val rxSinceBoot: Long?,
    val txSinceBoot: Long?,
    val batteryPercent: Int?,
    val batteryPercentPerHour: Int?,
    val cpuPercent: Int?,
    val ioBytesPerSec: Long?,
    val vpnActive: Boolean?,
    val metered: Boolean?,
    /** SystemClock.elapsedRealtime() when this set of readings was taken (monotonic; wall-clock changes do not age it). */
    val observedAtElapsedMs: Long? = null,
)

object DeviceResourcesReader {
    private val cpuTracker = CpuRateTracker()
    private val diskTracker = DiskRateTracker()

    /**
     * Test seam only: the screenshot suite sets this so renders do not depend on the build machine's /proc CPU and
     * disk counters. Production code never assigns it; null means "read the real device".
     */
    @androidx.annotation.VisibleForTesting
    @Volatile
    internal var testSource: ((Context) -> DeviceResources)? = null

    fun read(context: Context): DeviceResources {
        testSource?.let { return it(context) }
        val stat = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
        val rx = TrafficStats.getTotalRxBytes().takeIf { it >= 0 }
        val tx = TrafficStats.getTotalTxBytes().takeIf { it >= 0 }
        val battery = DeviceObservations.read(context)
        val cpu = cpuTracker.observe(CpuSample.read())
        // Monotonic clock: wall-clock changes cannot stretch or shrink the interval.
        val io = diskTracker.observe(DiskSample.read(), android.os.SystemClock.elapsedRealtime())
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val vpn = cm?.allNetworks?.any { network ->
            cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val metered = caps?.let {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                !it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
        return DeviceResources(
            storageTotal = stat?.totalBytes,
            storageFree = stat?.availableBytes,
            rxSinceBoot = rx,
            txSinceBoot = tx,
            batteryPercent = battery.batteryPercent,
            batteryPercentPerHour = drainPerHour(context, battery.charging),
            cpuPercent = cpu,
            ioBytesPerSec = io,
            vpnActive = vpn,
            metered = metered,
            observedAtElapsedMs = android.os.SystemClock.elapsedRealtime(),
        )
    }

    private fun drainPerHour(context: Context, charging: Boolean?): Int? {
        val bm = context.getSystemService(BatteryManager::class.java) ?: return null
        return BatteryDrain.percentPerHour(
            currentMicroamps = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            chargeCounterUah = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
            capacityPercent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            charging = charging,
        )
    }
}

/**
 * Instantaneous drain estimate from BATTERY_PROPERTY_CURRENT_NOW. The sign of that current differs between devices,
 * so its magnitude is used, but only while the battery is known to be discharging: a charging (or unknown) state
 * returns null instead of presenting charge current as an hourly drain.
 */
object BatteryDrain {
    fun percentPerHour(currentMicroamps: Int, chargeCounterUah: Int, capacityPercent: Int, charging: Boolean?): Int? {
        if (charging != false) return null
        if (currentMicroamps == Int.MIN_VALUE || chargeCounterUah == Int.MIN_VALUE || capacityPercent !in 1..100) return null
        if (currentMicroamps == 0 || chargeCounterUah <= 0) return null
        val fullUah = chargeCounterUah.toLong() * 100 / capacityPercent
        if (fullUah <= 0) return null
        return (abs(currentMicroamps.toLong()) * 100 / fullUah).toInt().coerceIn(0, 500)
    }
}

/**
 * Freshness of a displayed set of device readings (audit s.11: loading, unavailable, stale and valid are different).
 * A failed re-read never silently keeps showing the old numbers as current.
 */
object ReadingFreshness {
    const val STALE_AFTER_MS = 60_000L

    enum class State { NOT_READ, FRESH, STALE, LAST_READ_FAILED }

    fun state(observedAtElapsedMs: Long?, nowElapsedMs: Long, lastReadFailed: Boolean): State = when {
        observedAtElapsedMs == null -> State.NOT_READ
        lastReadFailed -> State.LAST_READ_FAILED
        // A sample "from the future" (clock source mismatch) is not trusted as fresh.
        nowElapsedMs < observedAtElapsedMs -> State.STALE
        nowElapsedMs - observedAtElapsedMs > STALE_AFTER_MS -> State.STALE
        else -> State.FRESH
    }

    fun label(observedAtElapsedMs: Long?, nowElapsedMs: Long, lastReadFailed: Boolean): String =
        when (state(observedAtElapsedMs, nowElapsedMs, lastReadFailed)) {
            State.NOT_READ -> if (lastReadFailed) "Device readings unavailable: the read failed" else "Device readings not taken yet"
            State.LAST_READ_FAILED -> "Stale: the last read failed, so these are the previous readings"
            State.STALE -> {
                val minutes = observedAtElapsedMs?.let { (nowElapsedMs - it).coerceAtLeast(0) / 60_000 }
                if (minutes != null && minutes > 0) "Stale: read $minutes min ago" else "Stale: reading age unknown"
            }
            State.FRESH -> "Read under a minute ago"
        }
}
