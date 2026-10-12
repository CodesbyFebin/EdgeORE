package com.edgeore.app.device

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Process
import android.os.StatFs

data class ResourceTelemetry(val cores: Int? = null, val memoryTotal: Long? = null, val memoryAvailable: Long? = null,
    val storageTotal: Long? = null, val storageFree: Long? = null, val connection: String = "Not observed",
    val appRx: Long? = null, val appTx: Long? = null, val at: Long? = null)

object ResourceTelemetryReader {
    @androidx.annotation.VisibleForTesting
    @Volatile internal var testSource: ((Context) -> ResourceTelemetry)? = null
    fun read(context: Context): ResourceTelemetry {
        testSource?.let { return it(context) }
        val memory = runCatching { ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) } }.getOrNull()
        val disk = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
        val connection = runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val caps = cm.activeNetwork?.let(cm::getNetworkCapabilities)
            when {
                caps == null -> "Offline"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Connected"
            }
        }.getOrDefault("Unavailable")
        return ResourceTelemetry(Runtime.getRuntime().availableProcessors().takeIf { it > 0 }, memory?.totalMem, memory?.availMem,
            disk?.totalBytes, disk?.availableBytes, connection,
            TrafficStats.getUidRxBytes(Process.myUid()).takeIf { it >= 0 }, TrafficStats.getUidTxBytes(Process.myUid()).takeIf { it >= 0 }, System.currentTimeMillis())
    }
}
