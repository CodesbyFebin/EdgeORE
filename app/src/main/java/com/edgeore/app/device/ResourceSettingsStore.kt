package com.edgeore.app.device

import android.content.Context
import android.content.SharedPreferences

/** The one place that maps [ResourceSettings] to SharedPreferences keys, shared by the ViewModel and the scheduled job. */
object ResourceSettingsStore {
    const val FILE = "edgeore.settings"
    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(FILE, 0)

    fun load(prefs: SharedPreferences) = ResourceSettings(
        edgeModeResumed = prefs.getBoolean("edge.resumed", false),
        chargeOnly = prefs.getBoolean("ctl.chargeOnly", true),
        thermalGuard = prefs.getBoolean("ctl.thermal", true),
        batteryReservePercent = prefs.getInt("ctl.reserve", 20),
        cpuLimitPercent = prefs.getInt("ctl.cpu", 50),
        dailyLimitLamports = prefs.getLong("budget.daily", 50_000_000L),
        contributionOptIn = prefs.getBoolean("sched.optIn", false),
    )

    fun save(prefs: SharedPreferences, s: ResourceSettings) {
        prefs.edit().putBoolean("edge.resumed", s.edgeModeResumed).putBoolean("ctl.chargeOnly", s.chargeOnly)
            .putBoolean("ctl.thermal", s.thermalGuard).putInt("ctl.reserve", s.batteryReservePercent)
            .putInt("ctl.cpu", s.cpuLimitPercent).putLong("budget.daily", s.dailyLimitLamports)
            .putBoolean("sched.optIn", s.contributionOptIn).apply()
    }
}
