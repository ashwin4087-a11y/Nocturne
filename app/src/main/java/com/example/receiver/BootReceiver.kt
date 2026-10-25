package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.data.db.NocturneDatabase
import com.example.data.model.JourneyStatus
import com.example.service.NocturneTrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = NocturneDatabase.getInstance(context)
                    val activeJourney = db.journeyDao().getActiveJourneyDirect()
                    if (activeJourney != null && activeJourney.status == JourneyStatus.ACTIVE) {
                        // Restart foreground tracking service to resume protective surveillance
                        val serviceIntent = Intent(context, NocturneTrackingService::class.java).apply {
                            action = NocturneTrackingService.ACTION_START_TRACKING
                            putExtra("EXTRA_JOURNEY_ID", activeJourney.id)
                        }
                        ContextCompat.startForegroundService(context, serviceIntent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
