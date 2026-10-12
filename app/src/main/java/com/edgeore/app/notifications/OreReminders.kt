package com.edgeore.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.edgeore.app.MainActivity
import com.edgeore.app.R
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Optional local help reminder. No campaign eligibility or earnings are inferred. */
object OreReminders {
    const val UNIQUE = "edgeore.optional-reminder"
    private const val CHANNEL = "edgeore-help"
    fun permitted(context: Context) = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    fun prefs(context: Context) = context.getSharedPreferences("edgeore.reminders", Context.MODE_PRIVATE)
    fun set(context: Context, enabled: Boolean) {
        val active = enabled && permitted(context)
        prefs(context).edit().putBoolean("enabled", active).apply()
        val work = WorkManager.getInstance(context)
        if (!active) { work.cancelUniqueWork(UNIQUE); return }
        val now = ZonedDateTime.now()
        var next = now.withHour(10).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<OreReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(java.time.Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS).build()
        work.enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
    fun canDeliver(enabled: Boolean, hour: Int, day: String, lastDay: String?) = enabled && hour in 9..20 && day != lastDay
    fun deliver(context: Context): Boolean {
        val p = prefs(context); val now = ZonedDateTime.now(); val day = now.toLocalDate().toString()
        if (!permitted(context) || !canDeliver(p.getBoolean("enabled", false), now.hour, day, p.getString("lastDay", null))) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Optional ORE Bot reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(42, NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Your edge, under your control")
            .setContentText("Review your node status and receipts when it suits you.")
            .setContentIntent(intent).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build())
        p.edit().putString("lastDay", day).apply()
        return true
    }
}
class OreReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = try { OreReminders.deliver(applicationContext); Result.success() } catch (_: Exception) { Result.failure() }
}
