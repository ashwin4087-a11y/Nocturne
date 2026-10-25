package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.JourneyEntity
import com.example.data.model.RouteDeviationState
import com.example.ui.theme.NocturneTheme

@Composable
fun RouteDeviationBanner(
    journey: JourneyEntity,
    modifier: Modifier = Modifier
) {
    val theme = NocturneTheme.colors

    // Derive current deviation state safely
    val devState = when {
        journey.gpsStale -> RouteDeviationState.STALE_GPS
        journey.referenceRouteSummary == null && journey.routeDistanceSource != "MAPPED_ROUTE" -> RouteDeviationState.UNAVAILABLE_GEOMETRY
        else -> try {
            RouteDeviationState.valueOf(journey.deviationState)
        } catch (e: Exception) {
            RouteDeviationState.NORMAL
        }
    }

    val crossTrackKm = (journey.crossTrackDistanceKm * 10).toInt() / 10.0

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("route_deviation_alert_banner")
    ) {
        when (devState) {
            RouteDeviationState.CONFIRMED_DEVIATION -> {
                Column {
                    HazardStripeBanner(
                        text = "CRITICAL: ROUTE DIVERGENCE CONFIRMED — Vehicle is ~${crossTrackKm} km off scheduled corridor.",
                        isCritical = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2D1614), RoundedCornerShape(2.dp))
                            .border(1.dp, Color(0xFFE53935), RoundedCornerShape(2.dp))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.AltRoute,
                                contentDescription = "Confirmed Route Deviation",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "SUSTAINED OFF-CORRIDOR TRAJECTORY",
                                    color = Color(0xFFFF8A80),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Vehicle trajectory diverged >2.0 km from reference route. Likely an unscheduled detour or alternate transit corridor. Arrival window and alarm triggers are expanding defensively.",
                                    color = Color(0xFFFFCDD2),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            RouteDeviationState.POSSIBLE_DEVIATION -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF332A15), RoundedCornerShape(2.dp))
                        .border(1.dp, theme.warn, RoundedCornerShape(2.dp))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Possible Route Deviation",
                            tint = theme.warn,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "POSSIBLE ROUTE DIVERGENCE (±${crossTrackKm} KM)",
                                    color = theme.warnText,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "VERIFYING",
                                    color = theme.warnText,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Position is offset from reference corridor. Awaiting consecutive corroborating fixes to filter brief intersection detours or urban GPS bounce.",
                                color = theme.ink,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }

            RouteDeviationState.STALE_GPS -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E2836), RoundedCornerShape(2.dp))
                        .border(1.dp, theme.navy, RoundedCornerShape(2.dp))
                        .padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GpsNotFixed,
                            contentDescription = "Stale GPS",
                            tint = theme.warnText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "GPS SIGNAL STALE — DEVIATION MONITORING SUSPENDED",
                                color = theme.warnText,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Awaiting satellite fix before evaluating corridor cross-track alignment.",
                                color = theme.muted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            RouteDeviationState.UNAVAILABLE_GEOMETRY -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.surface, RoundedCornerShape(2.dp))
                        .border(1.dp, theme.border, RoundedCornerShape(2.dp))
                        .padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Unmapped Geometry",
                            tint = theme.navy,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "CORRIDOR ESTIMATE ACTIVE — GEOMETRY UNMAPPED",
                                color = theme.navy,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Using ${journey.routeCircuityFactor}x circuity model. Point-to-point cross-track deviation detection unavailable without mapped polyline.",
                                color = theme.muted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            RouteDeviationState.NORMAL -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.surface, RoundedCornerShape(2.dp))
                        .border(1.dp, theme.border, RoundedCornerShape(2.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Route Normal",
                                tint = theme.okText,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CORRIDOR ALIGNED",
                                color = theme.okText,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "OFFSET: ±${crossTrackKm} KM",
                            color = theme.muted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
