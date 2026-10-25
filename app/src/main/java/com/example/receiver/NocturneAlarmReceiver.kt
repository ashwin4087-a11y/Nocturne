package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.service.NocturneAlarmManager

class NocturneAlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_ALARM_TRIGGER = "com.example.nocturne.ACTION_ALARM_TRIGGER"
        const val ACTION_DISMISS_ALARM = "com.example.nocturne.ACTION_DISMISS_ALARM"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ALARM_TRIGGER -> {
                val destination = intent.getStringExtra("EXTRA_DESTINATION") ?: "Your Destination"
                val eta = intent.getIntExtra("EXTRA_ETA", 30)
                NocturneAlarmManager.triggerAlarmNow(context, destination, eta)
            }
            ACTION_DISMISS_ALARM -> {
                NocturneAlarmManager.stopSoundAndVibration(context)
            }
        }
    }
}
