package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.receiver.NocturneAlarmReceiver
import com.example.ui.AlarmActivity

enum class AlarmScheduleStatus {
    EXACT,
    INEXACT_FALLBACK,
    FAILED
}

object NocturneAlarmManager {
    private const val TAG = "NocturneAlarm"
    private const val ALARM_REQUEST_CODE = 4401
    private var activeRingtone: Ringtone? = null

    @Volatile
    var lastScheduleStatus: AlarmScheduleStatus = AlarmScheduleStatus.EXACT
        private set

    /**
     * Schedules destination arrival alarm with defensive capability checking.
     * If exact alarm permission (SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM) is restricted or denied,
     * gracefully falls back to a standard RTC wakeup alarm and records INEXACT_FALLBACK status
     * so UI and passengers are informed of potential Doze delays.
     */
    fun scheduleExactAlarm(
        context: Context,
        triggerAtMillis: Long,
        destinationName: String,
        etaMinutes: Int
    ): AlarmScheduleStatus {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return AlarmScheduleStatus.FAILED.also { lastScheduleStatus = it }

        val intent = Intent(context, NocturneAlarmReceiver::class.java).apply {
            action = NocturneAlarmReceiver.ACTION_ALARM_TRIGGER
            putExtra("EXTRA_DESTINATION", destinationName)
            putExtra("EXTRA_ETA", etaMinutes)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                alarmManager.canScheduleExactAlarms()
            } catch (e: Exception) {
                false
            }
        } else {
            true
        }

        if (canExact) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
                lastScheduleStatus = AlarmScheduleStatus.EXACT
                return AlarmScheduleStatus.EXACT
            } catch (e: SecurityException) {
                Log.w(TAG, "Exact alarm permission restricted; falling back to standard wake-up")
            }
        }

        // Graceful inexact fallback: note that Doze mode may batch or delay standard RTC alarms
        return try {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
            lastScheduleStatus = AlarmScheduleStatus.INEXACT_FALLBACK
            AlarmScheduleStatus.INEXACT_FALLBACK
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm fallback")
            lastScheduleStatus = AlarmScheduleStatus.FAILED
            AlarmScheduleStatus.FAILED
        }
    }

    fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val intent = Intent(context, NocturneAlarmReceiver::class.java).apply {
            action = NocturneAlarmReceiver.ACTION_ALARM_TRIGGER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager?.cancel(pendingIntent)
        stopSoundAndVibration(context)
    }

    fun triggerAlarmNow(context: Context, destinationName: String, etaMinutes: Int) {
        // 1. Play audio alarm ringtone and start multi-cadence vibration
        startSoundAndVibration(context)

        // 2. Post high-priority Heads-Up Notification with Full-Screen Intent
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        val alarmNotif = NotificationHelper.buildAlarmNotification(context, destinationName, etaMinutes)
        notificationManager?.notify(NotificationHelper.ALARM_NOTIFICATION_ID, alarmNotif)

        // 3. Attempt direct activity launch if permissible
        try {
            val activityIntent = Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION
                putExtra("EXTRA_DESTINATION", destinationName)
                putExtra("EXTRA_ETA", etaMinutes)
            }
            context.startActivity(activityIntent)
        } catch (e: Exception) {
            // Handled gracefully: heads-up full-screen notification guarantees delivery
            Log.d(TAG, "Direct launch suppressed by OS background activity restrictions")
        }
    }

    fun startSoundAndVibration(context: Context) {
        try {
            stopSoundAndVibration(context)

            // Play Alarm ringtone
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, alarmUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtone?.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            ringtone?.play()
            activeRingtone = ringtone

            // Trigger multi-cadence vibration pattern: wake-up alert cadence
            val vibrator = getVibrator(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0, 800, 400, 800, 400, 1200)
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 800, 400, 800, 400, 1200), 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ringtone or vibration")
        }
    }

    fun stopSoundAndVibration(context: Context) {
        try {
            activeRingtone?.stop()
            activeRingtone = null

            val vibrator = getVibrator(context)
            vibrator?.cancel()

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            notificationManager?.cancel(NotificationHelper.ALARM_NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ringtone or vibration")
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
