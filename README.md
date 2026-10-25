# NOCTURNE — Uncertainty-Aware Transit Intelligence & Adaptive Destination Alarm

**NOCTURNE** is an Android-first transit intelligence application designed specifically for passengers travelling on long-distance buses and trains.

Conventional alarms depend on fixed schedule times: when the bus or train is delayed, the passenger wakes up hours too early; when it runs ahead of schedule or arrives abruptly, they risk missing their stop. Operator tracking is often unavailable, and mobile coverage frequently drops along rural intercity corridors.

NOCTURNE goes beyond simple distance-based proximity triggers. It combines real-time route-aware circuity modeling, stop-aware speed estimation, honesty about GPS uncertainty, transparent anomaly detection (unusual stops and route diversions), offline-first local execution, an independent GPS watchdog, durable alarm state reconciliation, and native Android foreground/exact alarm integration.

---

## Visual Identity: Trust-First Analog Utility

The application derives its design directly from the NOCTURNE brand gauge and Swiss technical publishing:

- **180-Degree Dual-Needle Gauge (`DualNeedleGauge.kt`):** A custom precision gauge featuring a solid needle representing the current ETA point estimate, and a dashed amber needle indicating the upper bound / projected interval.
- **Mechanical Odometer Drums (`OdometerDrums.kt`):** Recessed instrument well displaying remaining transit time (`HH:MM`) with pneumatic rolling animations and approximate (`~`) uncertainty indicators.
- **Perforated Ticket Slips (`PerforatedTicketSlip.kt`):** Scalloped circular serrated edge tickets displaying destination details, departure timestamps, and status stamps.
- **Rubber-Ink Status Stamps (`StatusStamp.kt`):** Tilted rectangular badges (`TRACKING`, `ALARM ARMED`, `GPS WEAK`, `STALE DATA`, `OFFLINE`, `ESTIMATE UNCERTAIN`, `POSSIBLE DISRUPTION`, `VEHICLE STOPPED`).
- **Hazard Striping (`HazardStripeBanner.kt`):** 45-degree repeating diagonal hazard bands used when unusual stops, route deviations, or stale GPS fixes are detected.
- **Drafting Grid Canvas (`DraftingGridBackground.kt`):** 24dp drafting background pattern giving the feel of a calibrated navigation instrument.
- **Dual Day/Night Themes:** High-contrast light canvas (`#F7FAFC`) and deep nocturnal chamber dark mode (`#0A1522`) with WCAG AA compliant contrast (avoiding bright reds, using navy alert surfaces and `#F4B942`/`#8A5A00` amber warning accents).

---

## Core Capabilities & Architectural Reliability

1. **Route-Aware vs. Straight-Line Distance (`GeoMath.kt`, `EtaEngine.kt`):**
   - Distinguishes great-circle straight-line distance from real-world road and rail network transit distance.
   - Applies corridor circuity modeling (1.25x for intercity highway bus networks, 1.15x for rail trunk lines, 1.0x direct/waypoints).
   - Prevents severe ETA underestimation caused by assuming direct flight over curved terrain.

2. **Stop-Aware Speed Modeling (`EtaEngine.kt`):**
   - Separates stopped-state detection from moving cruising speed (threshold: 3.5 km/h).
   - Eliminates artificial speed clamping (previously 18 km/h). Moving cruising speed is derived strictly from moving observations.
   - When a vehicle is stationary at a depot, intersection, or rest stop, travel time explicitly accounts for current stop duration plus expected delay, and widens the uncertainty interval rather than pretending the vehicle is moving.

3. **Independent GPS Watchdog (`NocturneTrackingService.kt`):**
   - A dedicated 20-second background watchdog monitors the age of the latest accepted GPS fix independently of incoming location callbacks.
   - Detects GPS loss (e.g. inside tunnels or when OEM power management suppresses callbacks) even if zero new fixes arrive.
   - Updates the visible notification, widens ETA bounds defensively, and triggers pending alarms if time elapsed reaches the wake-up threshold.

4. **Single Ordered Telemetry Pipeline (`NocturneTrackingService.kt`):**
   - Replaces concurrent per-fix coroutines with a buffered Kotlin Coroutines `Channel<RawLocationFix>`.
   - Guarantees race-free, sequential processing: observation validation → route distance calculation → stop-aware ETA → anomaly detection → durable alarm evaluation → Room database write.

5. **Durable Alarm State & Restart Reconciliation (`AlarmEvaluator.kt`, `NocturneAlarmManager.kt`):**
   - Persists scheduled exact alarm timestamps durably in Room (`targetScheduledAlarmMs`).
   - On app restart or reboot, `reconcileOnRestart()` verifies if the scheduled wake-up time elapsed while killed (triggering immediately) or re-arms `AlarmManager`.
   - Uses Android 10+ compliant High-Priority Notifications with Full-Screen Intent (`setFullScreenIntent`), accompanied by audio ringtone and looping vibration waveforms.

6. **Offline-First Local Persistence:**
   - Powered by Room Database (`NocturneDatabase`), caching active journeys, timestamped observation history, and anomaly events.
   - Synchronous cold-start theme application to eliminate flash of unstyled theme.
   - Zero dependence on cloud servers for the core destination alarm.

7. **Pre-Flight Readiness Checklist (`ReadinessCheckScreen`):**
   - Real-time audit of Fine Location, Background Location, Exact Alarm permission, Notification permission, and GPS hardware.
   - Guidance on battery optimization whitelisting and alarm volume verification.

8. **Deterministic Simulator & Test Harness:**
   - In-app simulator allowing live testing of Steady Cruise (65 km/h), Extended Stop (0 km/h), Early High-Speed Run (105 km/h), Route Diversion, and Tunnel Blackout (Tests Watchdog).
   - Instant "FIRE TEST WAKE-UP ALARM" button for verifying audio and lock-screen activation.

---

## Permissions Declared

- `android.permission.ACCESS_FINE_LOCATION` — Precise GPS positioning
- `android.permission.ACCESS_COARSE_LOCATION` — Cell/Wi-Fi positioning fallback
- `android.permission.ACCESS_BACKGROUND_LOCATION` — Surveillance while passenger is asleep
- `android.permission.FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_LOCATION` — Continuous service execution
- `android.permission.POST_NOTIFICATIONS` — Persistent journey progress & wake-up alerts
- `android.permission.SCHEDULE_EXACT_ALARM` & `USE_EXACT_ALARM` — Pinpoint wake-up timing
- `android.permission.WAKE_LOCK` — Screen wake-up on arrival alert
- `android.permission.VIBRATE` — Haptic alarm alert pattern
- `android.permission.RECEIVE_BOOT_COMPLETED` — Journey recovery across device restarts

---

## Build & Test Instructions

### Compile Application:
```bash
gradle assembleDebug
```

### Run Automated Unit & Robolectric Tests:
```bash
gradle :app:testDebugUnitTest
```

---

## Known Environmental Limitations

- **Physical Device OEM Restrictions:** Aggressive vendor battery optimizers (MIUI, ColorOS, EMUI) may terminate background location after extended periods if not explicitly set to "Unrestricted" in Android Settings.
- **Physical Tunnel GPS:** Hardware GPS satellites cannot penetrate deep mountain tunnels or underground metro lines; NOCTURNE's watchdog flags stale GPS after 4 minutes and widens uncertainty intervals accordingly.
