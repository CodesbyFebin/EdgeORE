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
)

object DeviceResourcesReader {
    private val cpuTracker = CpuRateTracker()
    private val diskTracker = DiskRateTracker()

    fun read(context: Context): DeviceResources {
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
