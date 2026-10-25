package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DestinationSearchState
import com.example.data.model.DestinationSuggestion
import com.example.data.model.DistanceSource
import com.example.data.model.RouteOption
import com.example.data.model.RouteSearchState
import com.example.data.model.TransitMode
import com.example.data.repository.RoutingRepository
import com.example.ui.components.DraftingGridBackground
import com.example.ui.components.HardShadowBox
import com.example.ui.components.PerforatedTicketSlip
import com.example.ui.components.TactileButton
import com.example.ui.components.TricolorStripe
import com.example.ui.theme.NocturneTheme
import com.example.ui.viewmodel.NocturneViewModel

@Composable
fun DestinationPickerScreen(
    defaultBufferMinutes: Int,
    viewModel: NocturneViewModel,
    onBack: () -> Unit,
    onStartJourney: (
        destinationName: String,
        lat: Double,
        lon: Double,
        bufferMinutes: Int,
        circuityFactor: Double,
        distanceSource: String,
        routeSummary: String?
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = NocturneTheme.colors
    val focusManager = LocalFocusManager.current

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val routeSearchState by viewModel.routeSearchState.collectAsState()
    val selectedDestination by viewModel.selectedDestination.collectAsState()
    val selectedRoute by viewModel.selectedRoute.collectAsState()
    val isStarting by viewModel.isStartingJourney.collectAsState()

    var manualLat by remember { mutableStateOf("8.7290") }
    var manualLon by remember { mutableStateOf("77.7126") }
    var manualName by remember { mutableStateOf("Tirunelveli Junction") }
    var showManualCoords by remember { mutableStateOf(false) }
    var selectedBuffer by remember { mutableIntStateOf(defaultBufferMinutes) }

    val bufferOptions = listOf(15, 20, 30, 45, 60)

    DraftingGridBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.surface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("picker_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = theme.ink
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Where are you headed?",
                        color = theme.ink,
                        fontSize = 17.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "TARGET CALIBRATION & ROUTE DISCOVERY",
                        color = theme.muted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            TricolorStripe()

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Destination Search Box
                item {
                    HardShadowBox(backgroundColor = theme.surface) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "DESTINATION SEARCH",
                                color = theme.muted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.onSearchQueryChanged(it) },
                                placeholder = {
                                    Text(
                                        "Search station, city or destination",
                                        fontSize = 13.sp,
                                        color = theme.muted
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = theme.navy
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.clearSearch() }) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear search",
                                                tint = theme.muted
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("destination_search_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = theme.navy,
                                    unfocusedBorderColor = theme.border
                                )
                            )
                        }
                    }
                }

                // 2. Search Results / Suggestions Drawer
                when (val state = searchState) {
                    is DestinationSearchState.Searching -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = theme.navy,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Searching transit hub directory...",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = theme.muted
                                    )
                                }
                            }
                        }
                    }

                    is DestinationSearchState.Success -> {
                        item {
                            Text(
                                text = "SUGGESTED TRANSIT HUBS (${state.suggestions.size})",
                                color = theme.muted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(state.suggestions) { suggestion ->
                            val isSelected = selectedDestination?.name == suggestion.name
                            SuggestionItemCard(
                                suggestion = suggestion,
                                isSelected = isSelected,
                                onSelect = {
                                    viewModel.selectDestination(suggestion)
                                    manualName = suggestion.name
                                    manualLat = suggestion.lat.toString()
                                    manualLon = suggestion.lon.toString()
                                    focusManager.clearFocus()
                                }
                            )
                        }
                    }

                    is DestinationSearchState.NoResults -> {
                        item {
                            HardShadowBox(backgroundColor = theme.surface) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "NO MATCHING HUBS FOUND",
                                        color = theme.warnText,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "No station matched \"${state.query}\". You can select from popular hubs below or enter manual GPS coordinates.",
                                        color = theme.muted,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }

                    is DestinationSearchState.Idle -> {
                        if (selectedDestination == null) {
                            item {
                                Text(
                                    text = "POPULAR TRANSIT DESTINATIONS",
                                    color = theme.muted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            items(RoutingRepository.PRESET_TRANSIT_STATIONS.take(6)) { preset ->
                                SuggestionItemCard(
                                    suggestion = preset,
                                    isSelected = selectedDestination?.name == preset.name,
                                    onSelect = {
                                        viewModel.selectDestination(preset)
                                        manualName = preset.name
                                        manualLat = preset.lat.toString()
                                        manualLon = preset.lon.toString()
                                    }
                                )
                            }
                        }
                    }

                    is DestinationSearchState.Error -> {
                        item {
                            Text(
                                text = "Search lookup error: ${state.message}",
                                color = theme.warnText,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // 3. Selected Target & Route Discovery Section
                val activeDest = selectedDestination ?: DestinationSuggestion(
                    id = "default_hub",
                    name = manualName,
                    locality = "Southern Terminus",
                    lat = manualLat.toDoubleOrNull() ?: 8.7290,
                    lon = manualLon.toDoubleOrNull() ?: 77.7126,
                    category = "Transit Terminal",
                    isLocalPreset = true
                )

                item {
                    PerforatedTicketSlip {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = null,
                                        tint = theme.navy,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "TARGET DESTINATION",
                                        color = theme.ink,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .background(theme.navyDeep, RoundedCornerShape(2.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (activeDest.isLocalPreset) "VERIFIED HUB" else "CUSTOM TARGET",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = activeDest.name,
                                color = theme.ink,
                                fontSize = 17.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${activeDest.locality} • ${activeDest.category}",
                                color = theme.muted,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Coordinates: ${String.format("%.4f", activeDest.lat)}, ${String.format("%.4f", activeDest.lon)}",
                                color = theme.warnText,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // 4. Transit Route Options
                item {
                    Text(
                        text = "TRANSIT ROUTE SELECTION & REFERENCE DATA",
                        color = theme.muted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                when (val routeState = routeSearchState) {
                    is RouteSearchState.Loading -> {
                        item {
                            HardShadowBox(backgroundColor = theme.surface) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = theme.navy,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "DISCOVERING TRANSIT CORRIDORS...",
                                            color = theme.ink,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Querying Google Routes API & corridor circuity modeling.",
                                            color = theme.muted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    is RouteSearchState.Success -> {
                        items(routeState.routes) { route ->
                            val isSelected = selectedRoute?.id == route.id
                            RouteOptionCard(
                                route = route,
                                isSelected = isSelected,
                                onSelect = { viewModel.selectRoute(route) }
                            )
                        }
                    }

                    is RouteSearchState.Error -> {
                        item {
                            HardShadowBox(backgroundColor = theme.surface) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "ROUTING LOOKUP DEGRADED",
                                        color = theme.warnText,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${routeState.message} Defaulting to local corridor circuity heuristic.",
                                        color = theme.muted,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TactileButton(
                                        text = "RETRY ROUTING",
                                        onClick = { viewModel.retryRouteSearch() },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    is RouteSearchState.Empty -> {
                        item {
                            Text(
                                text = routeState.reason,
                                color = theme.muted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    is RouteSearchState.Idle -> {
                        item {
                            TactileButton(
                                text = "CALCULATE CORRIDOR ROUTES",
                                onClick = { viewModel.retryRouteSearch() },
                                icon = Icons.AutoMirrored.Filled.AltRoute,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // 5. Route Preview & Milestone Summary
                val currentRoute = selectedRoute
                if (currentRoute != null) {
                    item {
                        HardShadowBox(backgroundColor = theme.surface) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Navigation,
                                        contentDescription = null,
                                        tint = theme.navy,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "CORRIDOR PREVIEW & REFERENCE SUMMARY",
                                        color = theme.ink,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(text = "TOTAL ROUTE DISTANCE", color = theme.muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                        Text(
                                            text = "${(currentRoute.distanceKm * 10).toInt() / 10.0} km",
                                            color = theme.ink,
                                            fontSize = 15.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Column {
                                        Text(text = "ESTIMATED DURATION", color = theme.muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                        val hrs = currentRoute.durationMinutes / 60
                                        val mins = currentRoute.durationMinutes % 60
                                        Text(
                                            text = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m",
                                            color = theme.warnText,
                                            fontSize = 15.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = theme.muted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = currentRoute.warningNotice ?: "Reference route models highway network contours.",
                                        color = theme.muted,
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 6. Wake-Up Buffer Setting
                item {
                    PerforatedTicketSlip {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Alarm,
                                        contentDescription = null,
                                        tint = theme.warnText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "WAKE-UP BUFFER",
                                        color = theme.ink,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "$selectedBuffer MIN PRIOR",
                                    color = theme.warnText,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "How far before arrival should the alarm alert you? 30 minutes is standard for luggage packing and disembarking.",
                                color = theme.muted,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                bufferOptions.forEach { buffer ->
                                    val isSelected = buffer == selectedBuffer
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .background(
                                                if (isSelected) theme.navy else theme.bg,
                                                RoundedCornerShape(2.dp)
                                            )
                                            .border(
                                                1.5.dp,
                                                if (isSelected) theme.navyDeep else theme.border,
                                                RoundedCornerShape(2.dp)
                                            )
                                            .clickable { selectedBuffer = buffer },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${buffer}m",
                                            color = if (isSelected) Color.White else theme.ink,
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 7. Manual Coordinates Accordion Toggle
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showManualCoords = !showManualCoords }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (showManualCoords) "▲ HIDE MANUAL COORDINATE INPUT" else "▼ ADVANCED MANUAL COORDINATES",
                            color = theme.muted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (showManualCoords) {
                    item {
                        HardShadowBox(backgroundColor = theme.surface) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                OutlinedTextField(
                                    value = manualName,
                                    onValueChange = { manualName = it },
                                    label = { Text("Destination Name") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = theme.navy,
                                        unfocusedBorderColor = theme.border
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = manualLat,
                                        onValueChange = { manualLat = it },
                                        label = { Text("Latitude") },
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = theme.navy,
                                            unfocusedBorderColor = theme.border
                                        )
                                    )
                                    OutlinedTextField(
                                        value = manualLon,
                                        onValueChange = { manualLon = it },
                                        label = { Text("Longitude") },
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = theme.navy,
                                            unfocusedBorderColor = theme.border
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // 8. Start Journey Button (with Debounce & Duplicate Prevention)
                item {
                    val finalDestName = activeDest.name.trim().take(100)
                    val finalLat = activeDest.lat
                    val finalLon = activeDest.lon
                    val isLatValid = finalLat in -90.0..90.0
                    val isLonValid = finalLon in -180.0..180.0
                    val isNameValid = finalDestName.isNotBlank()
                    val canStart = isNameValid && isLatValid && isLonValid && !isStarting

                    val effectiveCircuity = selectedRoute?.mode?.circuityFactor ?: 1.25
                    val effectiveSource = selectedRoute?.distanceSource?.name ?: DistanceSource.ESTIMATED_ROUTE.name
                    val effectiveSummary = selectedRoute?.summary

                    TactileButton(
                        text = if (isStarting) "INITIALIZING JOURNEY..." else "START ACTIVE TRACKING",
                        onClick = {
                            if (canStart) {
                                onStartJourney(
                                    finalDestName,
                                    finalLat,
                                    finalLon,
                                    selectedBuffer,
                                    effectiveCircuity,
                                    effectiveSource,
                                    effectiveSummary
                                )
                            }
                        },
                        icon = Icons.Default.PlayArrow,
                        testTag = "start_active_journey_button",
                        backgroundColor = if (canStart) theme.navy else theme.muted,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionItemCard(
    suggestion: DestinationSuggestion,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val theme = NocturneTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) theme.navyDeep else theme.surface,
                RoundedCornerShape(2.dp)
            )
            .border(
                1.5.dp,
                if (isSelected) theme.warn else theme.border,
                RoundedCornerShape(2.dp)
            )
            .clickable(onClick = onSelect)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = suggestion.name,
                    color = if (isSelected) Color.White else theme.ink,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${suggestion.locality} • ${suggestion.category}",
                    color = if (isSelected) Color(0xFF9DB0C4) else theme.muted,
                    fontSize = 11.sp
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = theme.warn,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun RouteOptionCard(
    route: RouteOption,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val theme = NocturneTheme.colors
    val modeIcon = when (route.mode) {
        TransitMode.HIGHWAY_BUS -> Icons.Default.DirectionsBus
        TransitMode.RAIL_CORRIDOR -> Icons.Default.DirectionsRailway
        TransitMode.DIRECT_TERRAIN -> Icons.Default.Explore
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) theme.navyDeep else theme.surface,
                RoundedCornerShape(2.dp)
            )
            .border(
                1.5.dp,
                if (isSelected) theme.warn else theme.border,
                RoundedCornerShape(2.dp)
            )
            .clickable(onClick = onSelect)
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = modeIcon,
                        contentDescription = null,
                        tint = if (isSelected) theme.warn else theme.navy,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = route.mode.label.uppercase(),
                        color = if (isSelected) Color.White else theme.ink,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .background(
                            if (route.distanceSource == DistanceSource.MAPPED_ROUTE) Color(0xFF2E7D32) else theme.navy,
                            RoundedCornerShape(2.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = route.distanceSource.label,
                        color = Color.White,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = route.summary,
                color = if (isSelected) Color(0xFFDCE5ED) else theme.muted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${(route.distanceKm * 10).toInt() / 10.0} km",
                    color = if (isSelected) theme.warn else theme.ink,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                val hrs = route.durationMinutes / 60
                val mins = route.durationMinutes % 60
                Text(
                    text = if (hrs > 0) "~${hrs}h ${mins}m" else "~${mins}m",
                    color = if (isSelected) Color.White else theme.ink,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
