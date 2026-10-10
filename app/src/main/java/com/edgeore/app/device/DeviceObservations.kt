package com.edgeore.app.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

/** Real device readings. Null means "not observed", never zero. */
data class DeviceSnapshot(
    val batteryPercent: Int?,
    val charging: Boolean?,
    val thermal: ThermalLevel?,
    val observedAt: Long,
    /** BatteryManager.EXTRA_BATTERY_LOW (API 28+); null when not reported. */
    val batteryLow: Boolean? = null,
)

enum class ThermalLevel(val label: String) { NONE("Normal"), LIGHT("Light"), MODERATE("Moderate"), SEVERE("Severe"), CRITICAL("Critical"), EMERGENCY("Emergency"), SHUTDOWN("Shutdown") }

object DeviceObservations {
    fun read(context: Context): DeviceSnapshot {
        val intent: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else null
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> true
            BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
            else -> null
        }
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            ThermalLevel.entries.getOrNull(pm.currentThermalStatus)
        } else null
        val low = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && intent?.hasExtra(BatteryManager.EXTRA_BATTERY_LOW) == true) {
            intent.getBooleanExtra(BatteryManager.EXTRA_BATTERY_LOW, false)
        } else null
        return DeviceSnapshot(pct, charging, thermal, System.currentTimeMillis(), low)
    }
}
