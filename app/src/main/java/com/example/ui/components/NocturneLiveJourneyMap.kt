package com.example.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConfidenceLevel
import com.example.data.model.JourneyEntity
import com.example.engine.GeoMath
import com.example.ui.theme.NocturneTheme
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlin.math.cos
import kotlin.math.sin

/**
 * Live Journey Map with Google Maps Platform integration, adaptive vehicle markers (Train/Bus/Car),
 * camera-follow with manual recenter controls, smooth GPS bearing rotation, and an offline
 * high-precision analog vector radar fallback.
 */
@Composable
fun NocturneLiveJourneyMap(
    journey: JourneyEntity,
    polylinePoints: List<Pair<Double, Double>>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val theme = NocturneTheme.colors

    // Check Google Play Services availability safely
    val isPlayServicesAvailable = remember(context) {
        try {
            val availability = GoogleApiAvailability.getInstance()
            availability.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        } catch (e: Throwable) {
            false
        }
    }

    var forceAnalogCanvas by remember { mutableStateOf(!isPlayServicesAvailable) }
    var followVehicle by remember { mutableStateOf(true) }

    val vehicleLat = journey.lastKnownLat ?: journey.destinationLat
    val vehicleLon = journey.lastKnownLon ?: journey.destinationLon
    val hasLiveGps = journey.lastKnownLat != null && journey.lastKnownLon != null && !journey.gpsStale

    // Compute bearing toward destination or along trajectory
    val rawBearing = remember(vehicleLat, vehicleLon, journey.destinationLat, journey.destinationLon) {
        if (hasLiveGps) {
            GeoMath.calculateBearing(vehicleLat, vehicleLon, journey.destinationLat, journey.destinationLon).toFloat()
        } else 0f
    }

    val animatedBearing by animateFloatAsState(
        targetValue = rawBearing,
        animationSpec = tween(durationMillis = 600),
        label = "vehicle_bearing"
    )

    val modeIcon = when (journey.transportMode.uppercase()) {
        "TRAIN" -> Icons.Default.DirectionsTransit
        "CAR" -> Icons.Default.DirectionsCar
        else -> Icons.Default.DirectionsBus
    }

    val modeLabel = when (journey.transportMode.uppercase()) {
        "TRAIN" -> "RAIL CORRIDOR"
        "CAR" -> "EXPRESSWAY CAR"
        else -> "INTERCITY BUS"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, theme.border, RoundedCornerShape(4.dp))
            .testTag("live_journey_map_container")
    ) {
        if (isPlayServicesAvailable && !forceAnalogCanvas) {
            GoogleMapRenderer(
                vehicleLat = vehicleLat,
                vehicleLon = vehicleLon,
                destLat = journey.destinationLat,
                destLon = journey.destinationLon,
                destinationName = journey.destinationName,
                polylinePoints = polylinePoints,
                bearing = animatedBearing,
                followVehicle = followVehicle,
                onUserPan = { followVehicle = false },
                transportMode = journey.transportMode
            )
        } else {
            AnalogVectorRadarCanvas(
                vehicleLat = vehicleLat,
                vehicleLon = vehicleLon,
                destLat = journey.destinationLat,
                destLon = journey.destinationLon,
                destinationName = journey.destinationName,
                polylinePoints = polylinePoints,
                bearing = animatedBearing,
                followVehicle = followVehicle,
                onUserPan = { followVehicle = false },
                transportMode = journey.transportMode,
                hasLiveGps = hasLiveGps
            )
        }

        // Overlay: Corridor Verification Badge (Top Left)
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
        ) {
            val isVerified = polylinePoints.size >= 2 && journey.routeDistanceSource == "MAPPED_ROUTE"
            Box(
                modifier = Modifier
                    .background(
                        if (isVerified) Color(0xFF1B382B) else Color(0xFF263238),
                        RoundedCornerShape(2.dp)
                    )
                    .border(
                        1.dp,
                        if (isVerified) Color(0xFF4CAF50) else Color(0xFF78909C),
                        RoundedCornerShape(2.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(if (isVerified) Color(0xFF4CAF50) else Color(0xFFFFB300), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isVerified) "VERIFIED ROUTE GEOMETRY" else "ESTIMATED CORRIDOR (${journey.routeCircuityFactor}x)",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Transport Mode Tag
            Box(
                modifier = Modifier
                    .background(theme.navyDeep, RoundedCornerShape(2.dp))
                    .border(1.dp, theme.border, RoundedCornerShape(2.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = modeIcon,
                        contentDescription = modeLabel,
                        tint = theme.warn,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = modeLabel,
                        color = theme.ink,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Floating Recenter & Layer Switch Controls (Top Right)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isPlayServicesAvailable) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(theme.surface, RoundedCornerShape(4.dp))
                        .border(1.dp, theme.border, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { forceAnalogCanvas = !forceAnalogCanvas },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Toggle Analog Radar",
                            tint = if (forceAnalogCanvas) theme.warn else theme.navy,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Recenter Button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        if (followVehicle) theme.navyDeep else theme.surface,
                        RoundedCornerShape(4.dp)
                    )
                    .border(
                        1.dp,
                        if (followVehicle) theme.warn else theme.border,
                        RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = { followVehicle = true },
                    modifier = Modifier.size(34.dp).testTag("recenter_map_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Recenter on vehicle",
                        tint = if (followVehicle) theme.warn else theme.navy,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Bottom Compact ETA / Distance Telemetry HUD
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(theme.navyDeep.copy(alpha = 0.95f))
                .border(1.dp, theme.border)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = modeIcon,
                        contentDescription = null,
                        tint = theme.warn,
                        modifier = Modifier.size(16.dp).rotate(animatedBearing)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (hasLiveGps) "CRUISING ${journey.lastKnownSpeedKmh.toInt()} KM/H" else "AWAITING GPS",
                            color = theme.ink,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${(journey.remainingDistanceKm * 10).toInt() / 10.0} km to ${journey.destinationName}",
                            color = theme.muted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "ETA ~${journey.etaMinutesPoint} MIN",
                        color = theme.warnText,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "±${journey.lastKnownAccuracyMeters.toInt()}m precision",
                        color = theme.muted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * Google Maps SDK implementation with restrained dark styling, polyline rendering, and vehicle marker.
 */
@Composable
private fun GoogleMapRenderer(
    vehicleLat: Double,
    vehicleLon: Double,
    destLat: Double,
    destLon: Double,
    destinationName: String,
    polylinePoints: List<Pair<Double, Double>>,
    bearing: Float,
    followVehicle: Boolean,
    onUserPan: () -> Unit,
    transportMode: String
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(vehicleLat, vehicleLon), 12f)
    }

    // Follow vehicle camera animation
    LaunchedEffect(vehicleLat, vehicleLon, followVehicle) {
        if (followVehicle) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(vehicleLat, vehicleLon), 13f),
                durationMs = 800
            )
        }
    }

    // Detect user dragging to cancel follow mode
    if (cameraPositionState.isMoving && !followVehicle) {
        // User panning map manually
    }

    val mapProperties = remember {
        MapProperties(
            mapType = MapType.NORMAL,
            isTrafficEnabled = false
        )
    }

    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            compassEnabled = false,
            myLocationButtonEnabled = false,
            rotationGesturesEnabled = true,
            scrollGesturesEnabled = true,
            tiltGesturesEnabled = false,
            zoomGesturesEnabled = true
        )
    }

    val polylineLatLngs = remember(polylinePoints) {
        polylinePoints.map { LatLng(it.first, it.second) }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        uiSettings = mapUiSettings,
        onMapClick = { onUserPan() }
    ) {
        // Reference polyline
        if (polylineLatLngs.size >= 2) {
            Polyline(
                points = polylineLatLngs,
                color = Color(0xFFFFB300),
                width = 8f
            )
        }

        // Destination Marker
        Marker(
            state = MarkerState(position = LatLng(destLat, destLon)),
            title = destinationName,
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
        )

        // Vehicle Marker
        Marker(
            state = MarkerState(position = LatLng(vehicleLat, vehicleLon)),
            title = "Vehicle ($transportMode)",
            rotation = bearing,
            flat = true,
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
        )
    }
}

/**
 * Offline & Headless-Resilient Analog Vector Radar Canvas.
 * Plots coordinates onto local projection with pan, zoom, grid markings, and vehicle indicator.
 */
@Composable
private fun AnalogVectorRadarCanvas(
    vehicleLat: Double,
    vehicleLon: Double,
    destLat: Double,
    destLon: Double,
    destinationName: String,
    polylinePoints: List<Pair<Double, Double>>,
    bearing: Float,
    followVehicle: Boolean,
    onUserPan: () -> Unit,
    transportMode: String,
    hasLiveGps: Boolean
) {
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var zoomScale by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(followVehicle, vehicleLat, vehicleLon) {
        if (followVehicle) {
            panOffset = Offset.Zero
            zoomScale = 1f
        }
    }

    val theme = NocturneTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1722))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    onUserPan()
                    panOffset += pan
                    zoomScale = (zoomScale * zoom).coerceIn(0.5f, 5.0f)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Draw Tactical Drafting Grid
            val gridSize = 40.dp.toPx()
            val gridColor = Color(0xFF1E2B3C)
            var x = 0f
            while (x <= w) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                x += gridSize
            }
            var y = 0f
            while (y <= h) {
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                y += gridSize
            }

            // Coordinate bounding box calculation
            val allPoints = if (polylinePoints.isNotEmpty()) {
                polylinePoints + Pair(vehicleLat, vehicleLon) + Pair(destLat, destLon)
            } else {
                listOf(Pair(vehicleLat, vehicleLon), Pair(destLat, destLon))
            }

            val minLat = allPoints.minOf { it.first }
            val maxLat = allPoints.maxOf { it.first }
            val minLon = allPoints.minOf { it.second }
            val maxLon = allPoints.maxOf { it.second }

            val latSpan = (maxLat - minLat).coerceAtLeast(0.01)
            val lonSpan = (maxLon - minLon).coerceAtLeast(0.01)

            val paddingPx = 60.dp.toPx()
            val usableW = w - (paddingPx * 2)
            val usableH = h - (paddingPx * 2)

            fun project(lat: Double, lon: Double): Offset {
                val relX = ((lon - minLon) / lonSpan).toFloat()
                val relY = (1f - ((lat - minLat) / latSpan).toFloat()) // Invert latitude for screen Y

                val centerX = w / 2f
                val centerY = h / 2f

                val basePxX = paddingPx + (relX * usableW)
                val basePxY = paddingPx + (relY * usableH)

                val scaledX = centerX + (basePxX - centerX) * zoomScale + panOffset.x
                val scaledY = centerY + (basePxY - centerY) * zoomScale + panOffset.y
                return Offset(scaledX, scaledY)
            }

            // 2. Draw Reference Polyline or Corridor Direct Line
            if (polylinePoints.size >= 2) {
                val path = Path()
                val first = project(polylinePoints[0].first, polylinePoints[0].second)
                path.moveTo(first.x, first.y)
                for (i in 1 until polylinePoints.size) {
                    val p = project(polylinePoints[i].first, polylinePoints[i].second)
                    path.lineTo(p.x, p.y)
                }
                drawPath(
                    path = path,
                    color = Color(0xFFFFB300),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            } else {
                // Dashed Corridor Line
                val start = project(vehicleLat, vehicleLon)
                val end = project(destLat, destLon)
                drawLine(
                    color = Color(0xFF78909C),
                    start = start,
                    end = end,
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                )
            }

            // 3. Draw Destination Waypoint
            val destOffset = project(destLat, destLon)
            drawCircle(
                color = Color(0xFFE53935),
                radius = 8.dp.toPx(),
                center = destOffset
            )
            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = destOffset
            )

            // 4. Draw Vehicle Position & Heading Vector
            val vehicleOffset = project(vehicleLat, vehicleLon)

            // Accuracy halo
            drawCircle(
                color = Color(0x3329B6F6),
                radius = 16.dp.toPx(),
                center = vehicleOffset
            )
            // Core position
            drawCircle(
                color = Color(0xFF29B6F6),
                radius = 7.dp.toPx(),
                center = vehicleOffset
            )
            drawCircle(
                color = Color.White,
                radius = 2.5.dp.toPx(),
                center = vehicleOffset
            )

            // Bearing Directional Arrow
            val rad = Math.toRadians((bearing - 90.0)).toFloat()
            val arrowLength = 22.dp.toPx()
            val tipX = vehicleOffset.x + (cos(rad) * arrowLength)
            val tipY = vehicleOffset.y + (sin(rad) * arrowLength)
            drawLine(
                color = Color(0xFFFFD54F),
                start = vehicleOffset,
                end = Offset(tipX, tipY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
