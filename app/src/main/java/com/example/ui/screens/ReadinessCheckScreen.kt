package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ReadinessItem
import com.example.ui.components.DraftingGridBackground
import com.example.ui.components.HardShadowBox
import com.example.ui.components.PerforatedTicketSlip
import com.example.ui.components.StatusStamp
import com.example.ui.components.TactileButton
import com.example.ui.components.TricolorStripe
import com.example.ui.theme.NocturneTheme

@Composable
fun ReadinessCheckScreen(
    readinessItems: List<ReadinessItem>,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = NocturneTheme.colors
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefresh()
    }

    DraftingGridBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.surface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("readiness_back_button")
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
                        text = "PRE-FLIGHT READINESS CHECK",
                        color = theme.ink,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "HARDWARE, PERMISSIONS & BATTERY AUDIT",
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    PerforatedTicketSlip {
                        Column {
                            Text(
                                text = "CALIBRATION PROTOCOL",
                                color = theme.muted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Before going to sleep on a bus or train, verify that Android will not silence or terminate the application while the screen is locked.",
                                color = theme.ink,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // Permission audit rows
                items(readinessItems) { item ->
                    HardShadowBox(backgroundColor = theme.surface) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.title,
                                    color = theme.ink,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                StatusStamp(
                                    text = if (item.isReady) "READY" else "ACTION NEEDED",
                                    icon = if (item.isReady) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    color = if (item.isReady) theme.okText else theme.warnText,
                                    bgColor = if (item.isReady) theme.okBg else theme.warnBg
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = item.explanation,
                                color = theme.muted,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Grant all permissions action
                item {
                    TactileButton(
                        text = "GRANT / CONFIGURE PERMISSIONS",
                        onClick = {
                            val perms = mutableListOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                perms.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permissionLauncher.launch(perms.toTypedArray())
                        },
                        icon = Icons.Default.GpsFixed,
                        testTag = "grant_permissions_button",
                        backgroundColor = theme.navy,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Device Settings Shortcut
                item {
                    TactileButton(
                        text = "OPEN ANDROID SYSTEM SETTINGS",
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        isSecondary = true,
                        testTag = "open_settings_button",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Sleeping & Battery Advice Card
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.navyDeep, RoundedCornerShape(3.dp))
                            .border(1.dp, theme.border, RoundedCornerShape(3.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.BatteryAlert,
                                    contentDescription = null,
                                    tint = theme.warn,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SLEEPING PASSENGER ADVICE",
                                    color = theme.warn,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "• Keep your device charging if an outlet is available on your sleeper coach or train.\n" +
                                        "• In Android Battery settings, set NOCTURNE to 'Unrestricted' so OEM power management does not pause GPS updates.\n" +
                                        "• Set Alarm volume to high. NOCTURNE triggers an Android Alarm stream ringtone.\n" +
                                        "• Offline GPS continues to track without mobile data, but satellite signals can degrade in deep tunnels.",
                                color = Color(0xFFE8EEF4),
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
