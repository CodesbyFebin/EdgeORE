package com.edgeore.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.edgeore.app.MainActivity
import com.edgeore.app.R
import com.edgeore.app.edge.RoundAlerts

/** Local notifications from read-only polls while EdgeORE is open. Opt-in; no server, no push. */
object RoundAlertNotifier {
    private const val CHANNEL = "edgeore-round-alerts"

    fun post(
        context: Context,
        alert: RoundAlerts.Alert,
    ): Boolean {
        if (!OreReminders.permitted(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "ORE round alerts", NotificationManager.IMPORTANCE_DEFAULT))
        val intent =
            PendingIntent.getActivity(
                context,
                1,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val title =
            when (alert.kind) {
                RoundAlerts.Kind.ROUND_ENDING -> "Round ending"
                RoundAlerts.Kind.MOTHERLODE_HIT -> "Motherlode hit"
                RoundAlerts.Kind.YOU_WON -> "Your squares won"
            }
        manager.notify(
            100 + alert.kind.ordinal,
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(alert.text)
                .setContentIntent(intent)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build(),
        )
        return true
    }
}
