package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.ui.AlarmActivity

object NotificationHelper {
    const val TRACKING_CHANNEL_ID = "nocturne_tracking_channel"
    const val ALARM_CHANNEL_ID = "nocturne_alarm_channel"
    const val TRACKING_NOTIFICATION_ID = 1001
    const val ALARM_NOTIFICATION_ID = 1002

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Tracking Channel (Ongoing, quiet, lock screen private)
            val trackingChannel = NotificationChannel(
                TRACKING_CHANNEL_ID,
                "Nocturne Journey Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active journey ETA, distance, and alarm readiness."
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            notificationManager.createNotificationChannel(trackingChannel)

            // Alarm Channel (Critical, high priority, heads-up)
            val alarmChannel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "Nocturne Arrival Alarm",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Destination wake-up alarms and critical transit alerts."
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 1200)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(alarmChannel)
        }
    }

    /**
     * Builds the redacted public notification version shown when the lock screen is locked.
     * Prevents shoulder surfers or lock screen watchers from reading precise destination coordinates or names.
     */
    private fun buildPublicTrackingNotification(context: Context): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, TRACKING_CHANNEL_ID)
            .setContentTitle("NOCTURNE")
            .setContentText("Transit Journey Tracking Active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    fun buildTrackingNotification(
        context: Context,
        destinationName: String,
        etaMinutes: Int,
        etaRange: String,
        distanceKm: Double,
        statusText: String,
        isGpsStale: Boolean = false
    ): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val endIntent = Intent(context, NocturneTrackingService::class.java).apply {
            action = NocturneTrackingService.ACTION_STOP_TRACKING
        }
        val endPendingIntent = PendingIntent.getService(
            context,
            1,
            endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isGpsStale) {
            "NOCTURNE: $destinationName [GPS STALE · TRACKING DEGRADED]"
        } else {
            "NOCTURNE: $destinationName"
        }

        val content = if (isGpsStale) {
            "GPS STALE · TRACKING DEGRADED • ETA ~${etaMinutes}m ($etaRange) • ${(distanceKm * 10).toInt() / 10.0} km"
        } else {
            "ETA ~${etaMinutes}m ($etaRange) • ${(distanceKm * 10).toInt() / 10.0} km • $statusText"
        }

        val publicVersion = buildPublicTrackingNotification(context)

        return NotificationCompat.Builder(context, TRACKING_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End Journey", endPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build()
    }

    fun buildAlarmNotification(
        context: Context,
        destinationName: String,
        etaMinutes: Int
    ): Notification {
        val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_DESTINATION", destinationName)
            putExtra("EXTRA_ETA", etaMinutes)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            2,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, AlarmActivity::class.java).apply {
            action = "ACTION_DISMISS_ALARM"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val dismissPendingIntent = PendingIntent.getActivity(
            context,
            3,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setContentTitle("WAKE UP — APPROACHING DESTINATION")
            .setContentText("Estimated arrival at $destinationName in approximately $etaMinutes minutes.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_lock_power_off, "Dismiss Alarm", dismissPendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()
    }
}
