package com.edgeore.app.screens

import com.edgeore.app.device.DeviceResources
import com.edgeore.app.device.DeviceResourcesReader
import org.junit.rules.ExternalResource

/**
 * Screenshot-test double: every device reading is "not observed" (null), so a render never shows the build
 * machine's /proc CPU or disk numbers. Nothing is invented: the screen shows exactly what it shows on a device
 * that reports nothing. Must be ordered before the Compose rule so the first Storage read already uses it.
 */
class FixedDeviceReadings : ExternalResource() {
    override fun before() {
        DeviceResourcesReader.testSource = { UNOBSERVED.copy(observedAtElapsedMs = android.os.SystemClock.elapsedRealtime()) }
    }
    override fun after() { DeviceResourcesReader.testSource = null }

    companion object {
        val UNOBSERVED = DeviceResources(
            storageTotal = null, storageFree = null, rxSinceBoot = null, txSinceBoot = null,
            batteryPercent = null, batteryPercentPerHour = null, cpuPercent = null, ioBytesPerSec = null,
            vpnActive = null, metered = null,
        )
    }
}
