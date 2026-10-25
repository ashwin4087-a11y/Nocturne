package com.example.ui.viewmodel

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.DestinationSearchState
import com.example.data.model.DestinationSuggestion
import com.example.data.model.JourneyEntity
import com.example.data.model.ReadinessItem
import com.example.data.model.RouteOption
import com.example.data.model.RouteSearchState
import com.example.data.model.SimulationScenario
import com.example.data.model.ThemePreference
import com.example.data.repository.ActiveRouteHolder
import com.example.data.repository.JourneyRepository
import com.example.data.repository.RoutingRepository
import com.example.data.repository.UserSettingsRepository
import com.example.service.NocturneAlarmManager
import com.example.service.NocturneTrackingService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class NocturneViewModel(
    private val context: Context,
    private val journeyRepo: JourneyRepository,
    private val settingsRepo: UserSettingsRepository,
    private val routingRepo: RoutingRepository = RoutingRepository()
) : ViewModel() {

    val activeJourney: StateFlow<JourneyEntity?> = journeyRepo.activeJourneyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val journeyHistory: StateFlow<List<JourneyEntity>> = journeyRepo.allJourneysFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val themePreference: StateFlow<ThemePreference> = settingsRepo.themeFlow
    val currentScenario: StateFlow<SimulationScenario> = NocturneTrackingService.currentScenarioFlow

    private val _readinessState = MutableStateFlow<List<ReadinessItem>>(emptyList())
    val readinessState = _readinessState.asStateFlow()

    private val _isExactAlarmPermitted = MutableStateFlow(true)
    val isExactAlarmPermitted = _isExactAlarmPermitted.asStateFlow()

    // --- Destination Search & Routing UI State ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchState = MutableStateFlow<DestinationSearchState>(DestinationSearchState.Idle)
    val searchState: StateFlow<DestinationSearchState> = _searchState.asStateFlow()

    private val _routeSearchState = MutableStateFlow<RouteSearchState>(RouteSearchState.Idle)
    val routeSearchState: StateFlow<RouteSearchState> = _routeSearchState.asStateFlow()

    private val _selectedDestination = MutableStateFlow<DestinationSuggestion?>(null)
    val selectedDestination: StateFlow<DestinationSuggestion?> = _selectedDestination.asStateFlow()

    private val _selectedRoute = MutableStateFlow<RouteOption?>(null)
    val selectedRoute: StateFlow<RouteOption?> = _selectedRoute.asStateFlow()

    private val _isStartingJourney = MutableStateFlow(false)
    val isStartingJourney: StateFlow<Boolean> = _isStartingJourney.asStateFlow()

    private var searchJob: Job? = null
    private var routeJob: Job? = null
    private val startJourneyInProgress = AtomicBoolean(false)

    init {
        refreshReadiness()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()

        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _searchState.value = DestinationSearchState.Idle
            return
        }

        searchJob = viewModelScope.launch {
            delay(300L) // 300ms debounce
            routingRepo.searchDestinations(trimmed).collect { state ->
                _searchState.value = state
            }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchState.value = DestinationSearchState.Idle
        searchJob?.cancel()
    }

    fun selectDestination(
        suggestion: DestinationSuggestion,
        originLat: Double? = null,
        originLon: Double? = null
    ) {
        _selectedDestination.value = suggestion
        _selectedRoute.value = null

        // Trigger route discovery for the selected target
        fetchRoutes(
            originLat = originLat ?: 13.0827, // Default Chennai Central baseline if origin unknown
            originLon = originLon ?: 80.2707,
            destLat = suggestion.lat,
            destLon = suggestion.lon,
            destName = suggestion.name
        )
    }

    fun selectRoute(route: RouteOption) {
        _selectedRoute.value = route
        ActiveRouteHolder.activePolylinePoints = route.polylinePoints
        ActiveRouteHolder.activeRouteOption = route
    }

    fun fetchRoutes(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        destName: String
    ) {
        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            routingRepo.getRouteOptions(originLat, originLon, destLat, destLon, destName).collect { state ->
                _routeSearchState.value = state
                if (state is RouteSearchState.Success && state.routes.isNotEmpty()) {
                    val defaultRoute = state.routes[0]
                    _selectedRoute.value = defaultRoute
                    ActiveRouteHolder.activePolylinePoints = defaultRoute.polylinePoints
                    ActiveRouteHolder.activeRouteOption = defaultRoute
                }
            }
        }
    }

    fun retryRouteSearch(originLat: Double? = null, originLon: Double? = null) {
        val dest = _selectedDestination.value ?: return
        fetchRoutes(
            originLat = originLat ?: 13.0827,
            originLon = originLon ?: 80.2707,
            destLat = dest.lat,
            destLon = dest.lon,
            destName = dest.name
        )
    }

    fun refreshReadiness() {
        val list = mutableListOf<ReadinessItem>()

        // 1. Fine Location
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        list.add(
            ReadinessItem(
                key = "FINE_LOCATION",
                title = "Precise Location Permission",
                explanation = "Required to calculate real remaining distance and velocity toward destination.",
                isReady = fineLocationGranted
            )
        )

        // 2. Background Location
        val bgLocationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        list.add(
            ReadinessItem(
                key = "BG_LOCATION",
                title = "Background Location Access",
                explanation = "Allows continuous journey tracking while phone screen is off or passenger sleeps.",
                isReady = bgLocationGranted,
                isOptional = false
            )
        )

        // 3. Exact Alarm
        val exactAlarmGranted = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                alarmManager?.canScheduleExactAlarms() ?: true
            } else {
                true
            }
        } catch (e: Throwable) {
            true
        }
        list.add(
            ReadinessItem(
                key = "EXACT_ALARM",
                title = "Exact Alarm Permission",
                explanation = "Enables pinpoint wake-up scheduling without OS battery-saver delays.",
                isReady = exactAlarmGranted
            )
        )

        // 4. Notifications
        val notifGranted = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        } catch (e: Throwable) {
            true
        }
        list.add(
            ReadinessItem(
                key = "NOTIFICATIONS",
                title = "Notification Delivery",
                explanation = "Displays persistent journey tracking and critical arrival wake-up screens.",
                isReady = notifGranted
            )
        )

        // 5. GPS Hardware Services Enabled
        val gpsEnabled = try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        } catch (e: Throwable) {
            false
        }
        list.add(
            ReadinessItem(
                key = "GPS_HARDWARE",
                title = "GPS Satellite Receiver",
                explanation = "Device location sensor must be turned on to compute position.",
                isReady = gpsEnabled
            )
        )

        _readinessState.value = list
        _isExactAlarmPermitted.value = exactAlarmGranted
    }

    fun startJourney(
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
    ) {
        // Prevent race condition / duplicate journey creation from rapid taps
        if (!startJourneyInProgress.compareAndSet(false, true)) {
            return
        }
        _isStartingJourney.value = true
        ActiveRouteHolder.currentTransportMode = transportMode

        viewModelScope.launch {
            try {
                val journeyId = journeyRepo.startNewJourney(
                    destinationName = destinationName,
                    destinationLat = destinationLat,
                    destinationLon = destinationLon,
                    wakeUpBufferMinutes = wakeUpBufferMinutes,
                    circuityFactor = circuityFactor,
                    routeDistanceSource = routeDistanceSource,
                    referenceRouteSummary = referenceRouteSummary,
                    transportMode = transportMode,
                    ticketPnr = ticketPnr,
                    scheduledArrivalTimestamp = scheduledArrivalTimestamp,
                    originName = originName
                )

                val serviceIntent = Intent(context, NocturneTrackingService::class.java).apply {
                    action = NocturneTrackingService.ACTION_START_TRACKING
                    putExtra(NocturneTrackingService.EXTRA_JOURNEY_ID, journeyId)
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            } finally {
                _isStartingJourney.value = false
                startJourneyInProgress.set(false)
            }
        }
    }

    fun endJourney(completed: Boolean) {
        viewModelScope.launch {
            val active = activeJourney.value
            if (active != null) {
                journeyRepo.endJourney(active.id, completed)
            }
            val stopIntent = Intent(context, NocturneTrackingService::class.java).apply {
                action = NocturneTrackingService.ACTION_STOP_TRACKING
            }
            context.startService(stopIntent)
            NocturneAlarmManager.stopSoundAndVibration(context)
            ActiveRouteHolder.clear()
        }
    }

    fun setSimulationScenario(scenario: SimulationScenario) {
        val intent = Intent(context, NocturneTrackingService::class.java).apply {
            action = NocturneTrackingService.ACTION_SET_SCENARIO
            putExtra(NocturneTrackingService.EXTRA_SCENARIO, scenario.name)
        }
        context.startService(intent)
    }

    fun triggerTestAlarm() {
        val dest = activeJourney.value?.destinationName ?: "Manual Test Destination"
        val eta = activeJourney.value?.etaMinutesPoint ?: 15
        NocturneAlarmManager.triggerAlarmNow(context, dest, eta)
    }

    fun setTheme(pref: ThemePreference) {
        settingsRepo.setThemePreference(pref)
    }

    fun clearHistory() {
        viewModelScope.launch {
            journeyRepo.clearHistory()
        }
    }

    fun getDefaultBufferMinutes(): Int {
        return settingsRepo.getDefaultBufferMinutes()
    }

    fun setDefaultBufferMinutes(minutes: Int) {
        settingsRepo.setDefaultBufferMinutes(minutes)
    }

    fun isOnboardingCompleted(): Boolean {
        return settingsRepo.isOnboardingCompleted()
    }

    fun setOnboardingCompleted(completed: Boolean) {
        settingsRepo.setOnboardingCompleted(completed)
    }

    class Factory(
        private val context: Context,
        private val journeyRepo: JourneyRepository,
        private val settingsRepo: UserSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NocturneViewModel(context, journeyRepo, settingsRepo) as T
        }
    }
}
