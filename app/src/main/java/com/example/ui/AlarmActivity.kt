package com.example.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainActivity
import com.example.NocturneApplication
import com.example.service.NocturneAlarmManager
import com.example.service.NocturneTrackingService
import com.example.ui.components.HardShadowBox
import com.example.ui.components.HazardStripeBanner
import com.example.ui.components.StatusStamp
import com.example.ui.components.TactileButton
import com.example.ui.components.TricolorStripe
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NocturneTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Turn screen on and show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val destinationName = intent.getStringExtra("EXTRA_DESTINATION") ?: "Your Destination"
        val etaMinutes = intent.getIntExtra("EXTRA_ETA", 30)

        setContent {
            MyApplicationTheme {
                AlarmWakeScreen(
                    destinationName = destinationName,
                    etaMinutes = etaMinutes,
                    onDismissAndEnd = {
                        dismissAlarmAndEndJourney()
                    },
                    onKeepTracking = {
                        keepTrackingAndSilence()
                    }
                )
            }
        }
    }

    private fun dismissAlarmAndEndJourney() {
        NocturneAlarmManager.stopSoundAndVibration(this)

        val app = application as? NocturneApplication
        app?.let {
            CoroutineScope(Dispatchers.IO).launch {
                val active = it.journeyRepository.getActiveJourneyDirect()
                if (active != null) {
                    it.journeyRepository.endJourney(active.id, completed = true)
                }
            }
        }

        val stopIntent = Intent(this, NocturneTrackingService::class.java).apply {
            action = NocturneTrackingService.ACTION_STOP_TRACKING
        }
        startService(stopIntent)

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(mainIntent)
        finish()
    }

    private fun keepTrackingAndSilence() {
        NocturneAlarmManager.stopSoundAndVibration(this)
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(mainIntent)
        finish()
    }
}

@Composable
fun AlarmWakeScreen(
    destinationName: String,
    etaMinutes: Int,
    onDismissAndEnd: () -> Unit,
    onKeepTracking: () -> Unit
) {
    val theme = NocturneTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF061526)) // High-contrast night chamber
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(30.dp))

                // High-visibility hazard banner
                HazardStripeBanner(
                    text = "DESTINATION ARRIVAL ALERT — WAKE UP",
                    isCritical = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFFC95C5C), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Alarm Active",
                        tint = Color.White,
                        modifier = Modifier.size(46.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "APPROACHING DESTINATION",
                    color = Color(0xFFF4B942),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = destinationName.uppercase(),
                    color = Color.White,
                    fontSize = 26.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Time remaining callout card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C2A47), RoundedCornerShape(4.dp))
                        .border(2.dp, Color(0xFFF4B942), RoundedCornerShape(4.dp))
                        .padding(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "ESTIMATED ARRIVAL IN",
                            color = Color(0xFF9DB0C4),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "~$etaMinutes MINUTES",
                            color = Color(0xFFF4B942),
                            fontSize = 32.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Prepare luggage and verify personal belongings.",
                            color = Color(0xFFDCE5ED),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Big 64dp+ Action Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TactileButton(
                    text = "DISMISS & COMPLETE JOURNEY",
                    onClick = onDismissAndEnd,
                    isDanger = true,
                    icon = Icons.Default.Close,
                    testTag = "alarm_dismiss_button",
                    minHeight = 64.dp,
                    modifier = Modifier.fillMaxWidth()
                )

                TactileButton(
                    text = "I'M AWAKE (KEEP TRACKING)",
                    onClick = onKeepTracking,
                    isSecondary = true,
                    icon = Icons.Default.CheckCircle,
                    testTag = "alarm_keep_tracking_button",
                    minHeight = 64.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
