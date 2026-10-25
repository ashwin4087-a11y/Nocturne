package com.example.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.annotation.VisibleForTesting
import androidx.core.app.ServiceCompat
import com.example.data.db.NocturneDatabase
import com.example.data.model.AnomalyType
import com.example.data.model.ConfidenceLevel
import com.example.data.model.JourneyEntity
import com.example.data.model.JourneyStatus
import com.example.data.model.ObservationEntity
import com.example.data.model.SimulationScenario
import com.example.engine.AlarmEvaluator
import com.example.engine.AnomalyDetector
import com.example.engine.EtaEngine
import com.example.engine.GeoMath
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

data class RawLocationFix(
    val lat: Double,
    val lon: Double,
    val speedKmh: Double,
    val accuracyMeters: Float,
    val timestamp: Long
)

sealed interface TelemetryEvent {
    data class LocationFix(
        val fix: RawLocationFix,
        val receivedElapsedRealtime: Long,
        val generation: Long
    ) : TelemetryEvent

    data class WatchdogCheck(
        val checkElapsedRealtime: Long,
        val generationSnapshot: Long
    ) : TelemetryEvent
}

class NocturneTrackingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var locationManager: LocationManager? = null

    private var currentJourneyId: Long = -1L
    private var simulationJob: Job? = null
    private var watchdogJob: Job? = null

    // Monotonic fix tracking & generation counter for race-free sequential processing
    private var lastAcceptedFixElapsedRealtime: Long = 0L
    private var isMarkedStale: Boolean = false
    private val fixGeneration = AtomicLong(0L)

    // Ordered telemetry channel to guarantee sequential, race-free processing
    private val telemetryChannel = Channel<TelemetryEvent>(capacity = Channel.BUFFERED)

    companion object {
        const val ACTION_START_TRACKING = "com.example.nocturne.ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.example.nocturne.ACTION_STOP_TRACKING"
        const val ACTION_SET_SCENARIO = "com.example.nocturne.ACTION_SET_SCENARIO"
        const val EXTRA_JOURNEY_ID = "EXTRA_JOURNEY_ID"
        const val EXTRA_SCENARIO = "EXTRA_SCENARIO"

        const val WATCHDOG_INTERVAL_MS = 5_000L // Check every 5 seconds
        const val STALE_GPS_THRESHOLD_MS = 60_000L // Stale after 60 seconds silence

        // Injectable monotonic clock for testing
        var clock: ElapsedRealtimeClock = SystemElapsedRealtimeClock

        private val _currentScenarioFlow = MutableStateFlow(SimulationScenario.REAL_GPS)
        val currentScenarioFlow = _currentScenarioFlow.asStateFlow()

        private val _isTrackingActive = MutableStateFlow(false)
        val isTrackingActive = _isTrackingActive.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Sequential event pipeline consumer
        serviceScope.launch {
            for (event in telemetryChannel) {
                when (event) {
                    is TelemetryEvent.LocationFix -> {
                        processLocationFixSequentially(event.fix, event.receivedElapsedRealtime)
                    }
                    is TelemetryEvent.WatchdogCheck -> {
                        processWatchdogCheckSequentially(event.checkElapsedRealtime, event.generationSnapshot)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                val journeyId = intent.getLongExtra(EXTRA_JOURNEY_ID, -1L)
                startTracking(journeyId)
            }
            ACTION_STOP_TRACKING -> {
                stopTracking()
            }
            ACTION_SET_SCENARIO -> {
                val scenarioName = intent.getStringExtra(EXTRA_SCENARIO)
                val scenario = scenarioName?.let {
                    try { SimulationScenario.valueOf(it) } catch (e: Exception) { null }
                } ?: SimulationScenario.REAL_GPS
                setScenario(scenario)
            }
        }
        return START_STICKY
    }

    private fun startTracking(journeyId: Long) {
        currentJourneyId = journeyId
        _isTrackingActive.value = true

        // Initialize monotonic reference with journey-start time to handle missing initial fixes safely
        lastAcceptedFixElapsedRealtime = clock.elapsedRealtime()
        isMarkedStale = false

        val initialNotif = NotificationHelper.buildTrackingNotification(
            this,
            "Transit Journey",
            30,
            "Estimating",
            0.0,
            "Acquiring GPS fix..."
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.TRACKING_NOTIFICATION_ID,
                initialNotif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NotificationHelper.TRACKING_NOTIFICATION_ID, initialNotif)
        }

        // Reconcile durable alarm schedule on service restart / boot
        serviceScope.launch {
            val db = NocturneDatabase.getInstance(applicationContext)
            val journey = if (journeyId > 0) {
                db.journeyDao().getJourneyById(journeyId)
            } else {
                db.journeyDao().getActiveJourneyDirect()
            }

            if (journey != null && journey.status == JourneyStatus.ACTIVE) {
                reconcileAlarmSchedule(journey)
            }
        }

        startRealLocationUpdates()
        startGpsWatchdog()
    }

    private fun stopTracking() {
        _isTrackingActive.value = false
        stopLocationUpdates()
        simulationJob?.cancel()
        simulationJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        NocturneAlarmManager.cancelAlarm(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Dedicated lifecycle-aware Coroutine checking GPS freshness every 5 seconds.
     */
    private fun startGpsWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            while (isActive && _isTrackingActive.value) {
                delay(WATCHDOG_INTERVAL_MS)
                if (isActive && _isTrackingActive.value) {
                    val currentGen = fixGeneration.get()
                    val checkTime = clock.elapsedRealtime()
                    telemetryChannel.send(TelemetryEvent.WatchdogCheck(checkTime, currentGen))
                }
            }
        }
    }

    /**
     * Sequential watchdog check executed within the single ordered telemetry pipeline.
     */
    @VisibleForTesting
    suspend fun processWatchdogCheckSequentially(
        checkElapsedRealtime: Long,
        generationSnapshot: Long
    ) {
        // Race condition safeguard: if a newer location fix arrived after this check was enqueued, abort stale update
        if (fixGeneration.get() > generationSnapshot) {
            return
        }

        val silenceDurationMs = checkElapsedRealtime - lastAcceptedFixElapsedRealtime

        if (silenceDurationMs > STALE_GPS_THRESHOLD_MS) {
            // Avoid redundant database writes and notification updates while GPS remains stale
            if (isMarkedStale) {
                return
            }

            val db = NocturneDatabase.getInstance(applicationContext)
            val journey = db.journeyDao().getActiveJourneyDirect() ?: return
            if (journey.status != JourneyStatus.ACTIVE) return

            isMarkedStale = true

            // Set degraded state: confidence = UNRELIABLE, gpsStale = true, anomaly = STALE_GPS_DATA
            // Do NOT fabricate new ETA from stale coordinates; preserve existing ETA points.
            val updatedJourney = journey.copy(
                gpsStale = true,
                confidence = ConfidenceLevel.UNRELIABLE,
                anomalyType = AnomalyType.STALE_GPS_DATA,
                anomalyDetail = "GPS silent for ${(silenceDurationMs / 1000)}s · Tracking degraded"
            )
            db.journeyDao().updateJourney(updatedJourney)

            // Update foreground notification to clearly display "GPS STALE · TRACKING DEGRADED"
            val notif = NotificationHelper.buildTrackingNotification(
                context = applicationContext,
                destinationName = journey.destinationName,
                etaMinutes = journey.etaMinutesPoint,
                etaRange = "${journey.etaMinutesLower}-${journey.etaMinutesUpper}m",
                distanceKm = journey.remainingDistanceKm,
                statusText = "GPS STALE · TRACKING DEGRADED",
                isGpsStale = true
            )
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.notify(NotificationHelper.TRACKING_NOTIFICATION_ID, notif)
        } else {
            // GPS is not stale; ensure flag is reset if within threshold
            if (isMarkedStale) {
                isMarkedStale = false
            }
        }
    }

    private fun reconcileAlarmSchedule(journey: JourneyEntity) {
        val decision = AlarmEvaluator.reconcileOnRestart(
            persistedScheduledTimestamp = journey.targetScheduledAlarmMs,
            alreadyTriggered = journey.alarmTriggered,
            currentTimeMs = System.currentTimeMillis()
        )

        if (decision.shouldTriggerNow) {
            NocturneAlarmManager.triggerAlarmNow(
                applicationContext,
                journey.destinationName,
                journey.etaMinutesPoint
            )
        } else if (decision.shouldReschedule) {
            NocturneAlarmManager.scheduleExactAlarm(
                applicationContext,
                decision.targetTriggerTimeMs,
                journey.destinationName,
                journey.etaMinutesPoint
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRealLocationUpdates() {
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                .setMinUpdateIntervalMillis(3000L)
                .setMinUpdateDistanceMeters(5f)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    if (_currentScenarioFlow.value == SimulationScenario.REAL_GPS) {
                        result.lastLocation?.let { loc ->
                            enqueueLocationFix(
                                lat = loc.latitude,
                                lon = loc.longitude,
                                speedKmh = if (loc.hasSpeed()) loc.speed * 3.6 else 45.0,
                                accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else 20f,
                                timestamp = loc.time
                            )
                        }
                    }
                }
            }

            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                mainLooper
            )
        } catch (e: SecurityException) {
            android.util.Log.w("NocturneTracking", "Location update denied by system security policies")
        } catch (e: Exception) {
            android.util.Log.e("NocturneTracking", "Failed to start location updates")
        }
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient?.removeLocationUpdates(it)
        }
        locationCallback = null
    }

    fun setScenario(scenario: SimulationScenario) {
        _currentScenarioFlow.value = scenario
        simulationJob?.cancel()

        if (scenario != SimulationScenario.REAL_GPS) {
            startSimulationLoop(scenario)
        }
    }

    private fun startSimulationLoop(scenario: SimulationScenario) {
        simulationJob = serviceScope.launch {
            val db = NocturneDatabase.getInstance(applicationContext)
            var currentJourney = db.journeyDao().getActiveJourneyDirect() ?: return@launch
            val destLat = currentJourney.destinationLat
            val destLon = currentJourney.destinationLon

            var simLat = destLat - 0.50
            var simLon = destLon - 0.40
            var simSpeed = 70.0

            while (isActive && _isTrackingActive.value) {
                currentJourney = db.journeyDao().getActiveJourneyDirect() ?: break
                if (currentJourney.status != JourneyStatus.ACTIVE) break

                when (scenario) {
                    SimulationScenario.STEADY_CRUISE -> {
                        simSpeed = 65.0
                        simLat += (destLat - simLat) * 0.03
                        simLon += (destLon - simLon) * 0.03
                    }
                    SimulationScenario.EXTENDED_REST_STOP -> {
                        simSpeed = 0.5 // Vehicle stationary at rest stop
                    }
                    SimulationScenario.EARLY_FAST_RUN -> {
                        simSpeed = 105.0 // High-speed expressway run
                        simLat += (destLat - simLat) * 0.06
                        simLon += (destLon - simLon) * 0.06
                    }
                    SimulationScenario.OFF_ROUTE_DIVERSION -> {
                        simSpeed = 60.0
                        simLat -= 0.015 // Moving away from destination
                        simLon -= 0.015
                    }
                    SimulationScenario.GPS_TUNNEL_BLACKOUT -> {
                        delay(6000L) // No fixes emitted! Watchdog will detect silence > 60s
                        continue
                    }
                    else -> {}
                }

                enqueueLocationFix(
                    lat = simLat,
                    lon = simLon,
                    speedKmh = simSpeed,
                    accuracyMeters = 15f,
                    timestamp = System.currentTimeMillis()
                )

                delay(4000L)
            }
        }
    }

    @VisibleForTesting
    fun enqueueLocationFix(
        lat: Double,
        lon: Double,
        speedKmh: Double,
        accuracyMeters: Float,
        timestamp: Long
    ) {
        // Enforce boundary checks: reject malformed or out-of-range coordinates
        if (!GeoMath.isValidCoordinate(lat, lon)) {
            return
        }
        val safeSpeed = if (GeoMath.isValidSpeed(speedKmh)) speedKmh else 0.0
        val safeAccuracy = if (GeoMath.isValidAccuracy(accuracyMeters)) accuracyMeters else 50f
        val safeTimestamp = if (timestamp > 0) timestamp else System.currentTimeMillis()

        val gen = fixGeneration.incrementAndGet()
        val fix = RawLocationFix(lat, lon, safeSpeed, safeAccuracy, safeTimestamp)
        val event = TelemetryEvent.LocationFix(
            fix = fix,
            receivedElapsedRealtime = clock.elapsedRealtime(),
            generation = gen
        )
        telemetryChannel.trySend(event)
    }

    /**
     * Sequential, race-free processing of incoming location observations.
     */
    @VisibleForTesting
    suspend fun processLocationFixSequentially(
        fix: RawLocationFix,
        receivedElapsedRealtime: Long
    ) {
        val db = NocturneDatabase.getInstance(applicationContext)
        val journey = db.journeyDao().getActiveJourneyDirect() ?: return
        if (journey.status != JourneyStatus.ACTIVE) return

        // Update monotonic fix timestamp & clear stale state on GPS recovery
        lastAcceptedFixElapsedRealtime = receivedElapsedRealtime
        isMarkedStale = false

        // 1. Separate straight-line distance from route-aware distance
        val straightLineKm = GeoMath.calculateDistanceKm(
            fix.lat, fix.lon,
            journey.destinationLat, journey.destinationLon
        )
        // Avoid double-applying circuity when mapped route distance already models highway geometry
        val effectiveCircuity = if (journey.routeDistanceSource == "MAPPED_ROUTE") {
            journey.routeCircuityFactor.coerceIn(1.0, 1.15)
        } else {
            journey.routeCircuityFactor
        }
        val remainingRouteKm = GeoMath.calculateRouteDistanceKm(
            fix.lat, fix.lon,
            journey.destinationLat, journey.destinationLon,
            circuityFactor = effectiveCircuity
        )

        // 2. Persist Observation
        val observation = ObservationEntity(
            journeyId = journey.id,
            timestamp = fix.timestamp,
            lat = fix.lat,
            lon = fix.lon,
            speedKmh = fix.speedKmh,
            accuracyMeters = fix.accuracyMeters,
            distanceToDestKm = remainingRouteKm
        )
        db.observationDao().insertObservation(observation)

        val recentObservations = db.observationDao().getRecentObservations(journey.id)

        // 3. Stop-Aware and Route-Aware ETA Calculation
        val etaResult = EtaEngine.calculateEta(
            remainingRouteKm = remainingRouteKm,
            straightLineKm = straightLineKm,
            recentObservations = recentObservations,
            lastFixTimestamp = fix.timestamp,
            isRouteAware = journey.isRouteAware,
            currentTimeMs = fix.timestamp
        )

        // 4. Transparent Anomaly Detection with corridor geometry deviation support
        val activePolyline = com.example.data.repository.ActiveRouteHolder.activePolylinePoints
        val anomalyResult = AnomalyDetector.detectAnomaly(
            currentRemainingKm = remainingRouteKm,
            currentEtaMinutes = etaResult.pointMinutes,
            previousEtaMinutes = journey.etaMinutesPoint,
            lastFixTimestamp = fix.timestamp,
            recentObservations = recentObservations,
            currentTimeMs = fix.timestamp,
            currentLat = fix.lat,
            currentLon = fix.lon,
            currentAccuracyMeters = fix.accuracyMeters,
            referencePolyline = activePolyline
        )

        // 5. Durable Alarm Evaluation
        val alarmDecision = AlarmEvaluator.evaluate(
            etaResult = etaResult,
            wakeUpBufferMinutes = journey.wakeUpBufferMinutes,
            alreadyTriggered = journey.alarmTriggered,
            persistedTargetTriggerMs = journey.targetScheduledAlarmMs,
            currentTimeMs = fix.timestamp
        )

        var newAlarmTriggered = journey.alarmTriggered
        var alarmTriggeredTimestamp = journey.alarmTriggeredTimestamp
        var updatedScheduledTimeMs = journey.targetScheduledAlarmMs

        if (alarmDecision.shouldTriggerNow && !journey.alarmTriggered) {
            newAlarmTriggered = true
            alarmTriggeredTimestamp = System.currentTimeMillis()
            updatedScheduledTimeMs = fix.timestamp
            NocturneAlarmManager.triggerAlarmNow(
                applicationContext,
                journey.destinationName,
                etaResult.pointMinutes
            )
        } else if (alarmDecision.shouldReschedule) {
            updatedScheduledTimeMs = alarmDecision.targetTriggerTimeMs
            NocturneAlarmManager.scheduleExactAlarm(
                applicationContext,
                alarmDecision.targetTriggerTimeMs,
                journey.destinationName,
                etaResult.pointMinutes
            )
        }

        val eval = com.example.data.repository.ActiveRouteHolder.lastDeviationEvaluation
        val devStateStr = eval?.state?.name ?: (if (activePolyline.size < 2) "UNAVAILABLE_GEOMETRY" else "NORMAL")
        val crossTrackKm = eval?.crossTrackDistanceKm ?: 0.0

        // 6. Persist Updated Journey State Durably in Room (clearing gpsStale on recovery)
        val updatedJourney = journey.copy(
            lastKnownLat = fix.lat,
            lastKnownLon = fix.lon,
            lastKnownSpeedKmh = fix.speedKmh,
            lastKnownAccuracyMeters = fix.accuracyMeters,
            lastFixTimestamp = fix.timestamp,
            remainingDistanceKm = remainingRouteKm,
            straightLineDistanceKm = straightLineKm,
            etaMinutesPoint = etaResult.pointMinutes,
            etaMinutesLower = etaResult.lowerMinutes,
            etaMinutesUpper = etaResult.upperMinutes,
            confidence = if (anomalyResult.isAnomaly) anomalyResult.confidenceImpact else etaResult.confidence,
            anomalyType = anomalyResult.type,
            anomalyDetail = if (anomalyResult.isAnomaly) anomalyResult.explanation else etaResult.explanation,
            alarmTriggered = newAlarmTriggered,
            alarmTriggeredTimestamp = alarmTriggeredTimestamp,
            targetScheduledAlarmMs = updatedScheduledTimeMs,
            isVehicleStationary = etaResult.isStationary,
            stationaryDurationMs = etaResult.stationaryDurationMs,
            gpsStale = false, // Cleanly cleared on recovery
            deviationState = devStateStr,
            crossTrackDistanceKm = crossTrackKm
        )
        db.journeyDao().updateJourney(updatedJourney)

        // 7. Update Ongoing Foreground Notification
        val notif = NotificationHelper.buildTrackingNotification(
            context = applicationContext,
            destinationName = journey.destinationName,
            etaMinutes = etaResult.pointMinutes,
            etaRange = "${etaResult.lowerMinutes}-${etaResult.upperMinutes}m",
            distanceKm = remainingRouteKm,
            statusText = if (etaResult.isStationary) "STOPPED (${(etaResult.stationaryDurationMs/60000)}m)" else if (anomalyResult.isAnomaly) anomalyResult.type.name else etaResult.confidence.name,
            isGpsStale = false
        )
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NotificationHelper.TRACKING_NOTIFICATION_ID, notif)
    }

    override fun onDestroy() {
        stopLocationUpdates()
        simulationJob?.cancel()
        watchdogJob?.cancel()
        _isTrackingActive.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Testing hooks
    @VisibleForTesting
    fun getLastAcceptedFixElapsedRealtime(): Long = lastAcceptedFixElapsedRealtime

    @VisibleForTesting
    fun setLastAcceptedFixElapsedRealtimeForTest(time: Long) {
        lastAcceptedFixElapsedRealtime = time
    }

    @VisibleForTesting
    fun isMarkedStaleForTest(): Boolean = isMarkedStale

    @VisibleForTesting
    fun getFixGenerationForTest(): Long = fixGeneration.get()
}
