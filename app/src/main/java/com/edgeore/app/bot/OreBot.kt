package com.edgeore.app.bot

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.edgeore.app.device.CheckInState
import com.edgeore.app.device.DailyCheckIn
import java.util.concurrent.TimeUnit

/**
 * ORE Bot: text-only tips and opt-in local reminders. It has no wallet, RPC or signing dependency and cannot act;
 * every tip points at a normal reviewed control.
 */
object OreBot {
    const val PREFS = "edgeore.checkin"
    const val KEY_STATE = "state"
    const val KEY_OPT_IN = "reminders"
    const val KEY_NOTIFIED = "notifiedFor"
    const val CHANNEL = "orebot.reminders"
    const val WORK = "orebot-reminder"

    /** Deterministic help, grounded in what the screen already shows. */
    fun tips(
        walletConnected: Boolean,
        nodeOrGatewayConfigured: Boolean,
        checkInAvailable: Boolean,
        clusterLabel: String,
    ): List<String> =
        buildList {
            if (!walletConnected) add("Tap Connect to choose a Mobile Wallet Adapter wallet. EdgeORE never sees your seed phrase.")
            if (!nodeOrGatewayConfigured) {
                add(
                    "Mine needs a paired node (Nodes) or your storage gateway (Storage) before it can run anything.",
                )
            }
            if (checkInAvailable) add("A check-in slot is open. Checking in adds to your streak and points, saved on your phone.")
            add("Balances are read from $clusterLabel. Switch the network with the selector; nothing carries over between networks.")
            add("Deploy always opens the exact-message review. I can't sign, send or deploy anything for you.")
        }

    fun notificationsAllowed(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun schedule(
        ctx: Context,
        on: Boolean,
    ) {
        val wm = WorkManager.getInstance(ctx)
        if (on) {
            wm.enqueueUniquePeriodicWork(
                WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.HOURS).build(),
            )
        } else {
            wm.cancelUniqueWork(WORK)
        }
    }

    /** Posts at most one reminder per opened slot (dedup key: UTC day + slots used). Lock-screen text has no address or job data. */
    fun remindIfAvailable(
        ctx: Context,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.getBoolean(KEY_OPT_IN, false) || !notificationsAllowed(ctx)) return false
        val s = CheckInState.fromJson(p.getString(KEY_STATE, null))
        if (!DailyCheckIn.available(s, now)) return false
        val key = DailyCheckIn.utcDay(now) + ":" + DailyCheckIn.used(s, now)
        if (p.getString(KEY_NOTIFIED, null) == key) return false
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return false
        if (Build.VERSION.SDK_INT >=
            26
        ) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "ORE Bot reminders", NotificationManager.IMPORTANCE_DEFAULT))
        }
        nm.notify(
            7001,
            NotificationCompat
                .Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("ORE Bot")
                .setContentText("A check-in slot is available. Streak and points saved on your phone.")
                .setAutoCancel(true)
                .build(),
        )
        p.edit().putString(KEY_NOTIFIED, key).apply()
        return true
    }
}

class ReminderWorker(
    ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        OreBot.remindIfAvailable(applicationContext)
        return Result.success()
    }
}
