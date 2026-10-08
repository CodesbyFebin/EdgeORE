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
    private var lastCpu: CpuSample? = null
    private var lastDisk: DiskSample? = null
    private var lastDiskAt: Long = 0L

    fun read(context: Context): DeviceResources {
        val stat = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
        val rx = TrafficStats.getTotalRxBytes().takeIf { it >= 0 }
        val tx = TrafficStats.getTotalTxBytes().takeIf { it >= 0 }
        val battery = DeviceObservations.read(context)
        val cpuNow = CpuSample.read()
        val cpu = cpuNow?.let { now -> lastCpu?.let { CpuSample.percent(it, now) } }
        lastCpu = cpuNow
        val diskNow = DiskSample.read()
        val now = System.currentTimeMillis()
        val io = if (diskNow != null && lastDisk != null) ioBytesPerSecond(lastDisk!!, diskNow, now - lastDiskAt) else null
        lastDisk = diskNow ?: lastDisk
        lastDiskAt = now
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
            batteryPercentPerHour = drainPerHour(context),
            cpuPercent = cpu,
            ioBytesPerSec = io,
            vpnActive = vpn,
            metered = metered,
        )
    }

    private fun drainPerHour(context: Context): Int? {
        val bm = context.getSystemService(BatteryManager::class.java) ?: return null
        val microamps = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val remainingUah = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val percent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (microamps == Int.MIN_VALUE || remainingUah == Int.MIN_VALUE || percent !in 1..100) return null
        val fullUah = remainingUah.toLong() * 100 / percent
        if (fullUah <= 0) return null
        return (abs(microamps.toLong()) * 100 / fullUah).toInt().coerceIn(0, 500)
    }
}
