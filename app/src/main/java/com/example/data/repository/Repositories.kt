package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.db.JourneyDao
import com.example.data.db.ObservationDao
import com.example.data.model.ConfidenceLevel
import com.example.data.model.JourneyEntity
import com.example.data.model.JourneyStatus
import com.example.data.model.ObservationEntity
import com.example.data.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class JourneyRepository(
    private val journeyDao: JourneyDao,
    private val observationDao: ObservationDao
) {
    val activeJourneyFlow: Flow<JourneyEntity?> = journeyDao.getActiveJourneyFlow()
    val allJourneysFlow: Flow<List<JourneyEntity>> = journeyDao.getAllJourneysFlow()

    suspend fun getActiveJourneyDirect(): JourneyEntity? {
        return journeyDao.getActiveJourneyDirect()
    }

    suspend fun startNewJourney(
        destinationName: String,
        destinationLat: Double,
        destinationLon: Double,
        wakeUpBufferMinutes: Int,
        circuityFactor: Double = 1.25,
        routeDistanceSource: String = "ESTIMATED_ROUTE",
        referenceRouteSummary: String? = null,
        transportMode: String = "BUS",
        ticketPnr: String? = null,
        scheduledArrivalTimestamp: Long? = null,
        originName: String? = null
    ): Long {
        journeyDao.cancelAllActiveJourneys()
        val newJourney = JourneyEntity(
            destinationName = destinationName,
            destinationLat = destinationLat,
            destinationLon = destinationLon,
            wakeUpBufferMinutes = wakeUpBufferMinutes,
            routeCircuityFactor = circuityFactor,
            isRouteAware = true,
            status = JourneyStatus.ACTIVE,
            startTimestamp = System.currentTimeMillis(),
            routeDistanceSource = routeDistanceSource,
            referenceRouteSummary = referenceRouteSummary,
            transportMode = transportMode,
            ticketPnr = ticketPnr,
            scheduledArrivalTimestamp = scheduledArrivalTimestamp,
            originName = originName
        )
        return journeyDao.insertJourney(newJourney)
    }

    suspend fun updateJourney(journey: JourneyEntity) {
        journeyDao.updateJourney(journey)
    }

    suspend fun endJourney(journeyId: Long, completed: Boolean) {
        val current = journeyDao.getJourneyById(journeyId) ?: return
        val updated = current.copy(
            status = if (completed) JourneyStatus.COMPLETED else JourneyStatus.CANCELLED,
            completedTimestamp = System.currentTimeMillis()
        )
        journeyDao.updateJourney(updated)
    }

    suspend fun recordObservation(observation: ObservationEntity) {
        observationDao.insertObservation(observation)
    }

    suspend fun getRecentObservations(journeyId: Long): List<ObservationEntity> {
        return observationDao.getRecentObservations(journeyId)
    }

    suspend fun clearHistory() {
        journeyDao.clearAllJourneys()
        observationDao.clearAllObservations()
    }
}

class UserSettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("nocturne_settings", Context.MODE_PRIVATE)

    private val _themeFlow = MutableStateFlow(getThemePreference())
    val themeFlow = _themeFlow.asStateFlow()

    fun getThemePreference(): ThemePreference {
        val name = prefs.getString("theme_mode", ThemePreference.SYSTEM.name)
        return try {
            ThemePreference.valueOf(name ?: ThemePreference.SYSTEM.name)
        } catch (e: Exception) {
            ThemePreference.SYSTEM
        }
    }

    fun setThemePreference(pref: ThemePreference) {
        prefs.edit().putString("theme_mode", pref.name).apply()
        _themeFlow.value = pref
    }

    fun getDefaultBufferMinutes(): Int {
        return prefs.getInt("default_buffer_minutes", 30)
    }

    fun setDefaultBufferMinutes(minutes: Int) {
        prefs.edit().putInt("default_buffer_minutes", minutes).apply()
    }

    fun isReduceMotionEnabled(): Boolean {
        return prefs.getBoolean("reduce_motion", false)
    }

    fun setReduceMotionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("reduce_motion", enabled).apply()
    }

    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean("onboarding_done", false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean("onboarding_done", completed).apply()
    }
}
