package com.example

import android.app.Application
import com.example.data.db.NocturneDatabase
import com.example.data.repository.JourneyRepository
import com.example.data.repository.UserSettingsRepository
import com.example.service.NotificationHelper

class NocturneApplication : Application() {

    val database: NocturneDatabase by lazy {
        NocturneDatabase.getInstance(this)
    }

    val journeyRepository: JourneyRepository by lazy {
        JourneyRepository(database.journeyDao(), database.observationDao())
    }

    val userSettingsRepository: UserSettingsRepository by lazy {
        UserSettingsRepository(this)
    }

    override fun onCreate() {
        super.onCreate()
        try {
            NotificationHelper.createChannels(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
