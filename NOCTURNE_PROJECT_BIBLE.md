# NOCTURNE --- Project Bible

**Version:** 2.0\
**Updated:** 14 October 2026\
**Status:** Living product and engineering source of truth\
**Platform:** Native Android --- Kotlin + Jetpack Compose\
**Primary principle:** Trust before prediction. Never hide uncertainty
or fabricate journey information.

------------------------------------------------------------------------

## 1. Product Definition

**NOCTURNE is an Android-first, uncertainty-aware transit companion for
long-distance bus and train passengers.** It estimates arrival time from
passenger-side GPS, communicates confidence and disruption, detects
unusual stops or likely route deviations, and adapts a wake-up alarm so
passengers can rest without relying on continuous connectivity.

NOCTURNE is not merely a proximity alarm. Its value is the combination
of route-aware ETA, transparent uncertainty, anomaly detection,
resilient local alarms, and privacy-first journey handling.

### Core user problem

Passengers may sleep during long journeys while actual arrival times
change due to delays, early arrival, unexpected stops, diversions, weak
mobile coverage, or missing operator telemetry. A fixed alarm or simple
straight-line distance trigger can wake a passenger too early or too
late.

### Product promise

Help passengers make better-informed decisions about when they may
arrive and whether the journey is behaving as expected---without
claiming certainty the available data cannot support.

------------------------------------------------------------------------

## 2. Product Principles

1.  **Trust before precision:** Show ranges and confidence when
    supported; explain degraded estimates.
2.  **Offline-first core:** Local tracking, journey state, and alarm
    decisions must not depend on a successful backend request.
3.  **Passenger-side intelligence:** Do not assume access to a transport
    operator's live vehicle feed or booking API.
4.  **Evidence before alerts:** Distinguish possible anomalies from
    confirmed ones and suppress noise caused by poor GPS.
5.  **Actual routes versus estimates:** Never present a driving route or
    straight-line estimate as a verified bus/train itinerary.
6.  **Privacy by default:** Minimise collection, transmission, and
    retention of location and ticket data.
7.  **Explicit user control:** Users can review, correct, cancel, and
    delete journey data.
8.  **Measurable engineering:** Report only tests and performance
    results that were actually observed.
9.  **Graceful degradation:** Missing keys, connectivity, GPS,
    permissions, or route geometry should produce honest fallback
    states, not fabricated certainty.

------------------------------------------------------------------------

## 3. Target User and Primary Journey

### Primary user

A passenger on an intercity bus or train who wants to sleep or rest
without missing their destination.

### Intended flow

1.  Select a destination or import a ticket.
2.  Review origin, destination, transport mode, schedule, and other
    extracted fields.
3.  Confirm the journey and required permissions.
4.  Start tracking and see the current location, ETA range, confidence,
    route evidence, and alarm readiness.
5.  Let the local tracking and alarm system adapt as new GPS
    observations arrive.
6.  Receive clear information when GPS becomes stale, the ETA changes
    materially, an unexpected stop occurs, or a sustained route
    deviation is suspected.
7.  End or cancel the journey, with associated data cleaned up according
    to the retention rules.

The app must not promise a guaranteed warning interval or guaranteed
alarm delivery under every Android/device condition.

------------------------------------------------------------------------

## 4. Current Product Scope

### Core capabilities and existing code evidence

The latest inspected source archive is `nocturne(1).zip`, dated 13
October 2026. The following components were present in that archive;
presence in source is not proof of successful build or physical-device
validation.

-   Native Android app using Kotlin and Jetpack Compose.
-   Existing screens for home, destination selection, onboarding,
    readiness check, settings, and active journey.
-   Room database and journey entity for local state.
-   Foreground tracking service and GPS watchdog logic.
-   ETA engine and geospatial helpers.
-   Anomaly detector with states/types including unusually long stop,
    persistent route deviation, stale GPS data, and sudden ETA change.
-   Alarm scheduling/notification components.
-   Routing repository and Google Routes API integration.
-   In-memory active route holder and selected route state.
-   Active journey UI with GPS-stale and anomaly banners.
-   Existing API/configuration setup for Google routing and a Gemini
    Maps API wrapper.

**Important limitation:** route geometry was held in memory in the
inspected implementation. The routing implementation used a `DRIVE`
request for road-route data, with estimated corridor/fallback logic for
bus and rail. That geometry must not be represented as an actual
scheduled transit itinerary. Confirm persistence and current
implementation details before relying on them.

### Reported work from earlier development sessions

Earlier project notes reported: - route-distance heuristics; - a GPS
watchdog running every 20 seconds, with stale detection after a longer
no-fix interval; - Room-backed alarm reconciliation and a full-screen
alarm notification; - stop-aware ETA behaviour; - sequential telemetry
processing via a coroutine `Channel`; - red styling removed from the
core visual treatment; - tests reportedly passed in an earlier
development environment.

These are **historical reported results**, not independently verified
results for the latest archive. Re-run the current project's build and
tests before claiming they still pass.

------------------------------------------------------------------------

## 5. Architecture and Stack

### Actual implementation direction

The project has moved from an early React Native/TypeScript proposal to
a native Android implementation.

-   **Language/UI:** Kotlin, Jetpack Compose, Material 3.
-   **State and lifecycle:** Existing ViewModel and lifecycle-aware
    Compose patterns.
-   **Persistence:** Room.
-   **Location:** Google Play Services Location and a foreground
    tracking service.
-   **Routing:** Retrofit/API wrappers and Google Routes API
    integration.
-   **Core logic:** ETA engine, anomaly detector, geospatial utilities,
    alarm evaluator.
-   **Notifications/alarms:** Native Android notification and alarm
    components.
-   **Configuration:** Gradle configuration and environment/secret
    configuration pattern.

Do not reintroduce React Native or a separate backend as a requirement
unless a concrete need is established. A server is optional; core alarm
functionality remains local.

### Existing project areas observed

``` text
app/src/main/java/com/example/
├── MainActivity.kt
├── NocturneApplication.kt
├── data/
│   ├── api/
│   │   ├── GeminiMapsApi.kt
│   │   └── GoogleRoutesApi.kt
│   ├── db/
│   │   ├── Daos.kt
│   │   └── NocturneDatabase.kt
│   ├── model/
│   │   ├── Models.kt
│   │   └── RoutingModels.kt
│   └── repository/
│       ├── Repositories.kt
│       └── RoutingRepository.kt
├── engine/
│   ├── AlarmEvaluator.kt
│   ├── AnomalyDetector.kt
│   ├── EtaEngine.kt
│   └── GeoMath.kt
├── service/
│   ├── NocturneTrackingService.kt
│   ├── ElapsedRealtimeClock.kt
│   ├── NocturneAlarmManager.kt
│   └── NotificationHelper.kt
├── ui/
│   ├── ActiveJourneyScreen.kt
│   ├── DestinationPickerScreen.kt
│   ├── HomeScreen.kt
│   ├── OnboardingScreen.kt
│   ├── ReadinessCheckScreen.kt
│   ├── SettingsScreen.kt
│   └── components/
└── viewmodel/
```

This is a map of files observed in the latest archive, not a guarantee
that every filename or package path remains unchanged after future
edits. Inspect the repository before applying changes.

### Architectural rules

1.  Maintain one authoritative location stream and one authoritative
    journey state.
2.  Keep UI rendering separate from ETA, anomaly, routing, and alarm
    decisions.
3.  Make important state recoverable after process death.
4.  Treat route geometry, its provenance, and its confidence as explicit
    data.
5.  Keep alarm scheduling idempotent; avoid duplicate alarms.
6.  Use deterministic clocks and simulation inputs where practical for
    tests.
7.  Do not add large dependencies without checking project compatibility
    and app size.
8.  If the current project lacks Gradle wrapper files or build tooling,
    report that limitation instead of claiming a successful build.

------------------------------------------------------------------------

## 6. ETA and Uncertainty

### Required output

The ETA layer should expose a predicted arrival time or remaining
duration and communicate confidence/uncertainty when the evidence
supports it. A point estimate alone must not imply certainty.

### Evidence hierarchy

1.  Valid, fresh GPS observations with known accuracy.
2.  Route distance derived from verified route geometry.
3.  Explicitly labelled estimated corridor geometry.
4.  Conservative distance/speed fallback when no route geometry exists.
5.  Scheduled ticket arrival time as a reference only---not as live
    predicted arrival.

### Rules

-   Do not treat straight-line distance as route distance.
-   Do not imply live traffic access unless a current, valid source
    provides it.
-   Widen uncertainty when GPS is stale, route provenance is weak, or
    deviation is suspected.
-   Avoid recalculating the alarm aggressively from a single noisy fix.
-   Keep **scheduled arrival**, **predicted ETA**, and **actual
    arrival** separate.
-   Record the provenance of a distance/ETA estimate so the UI can
    explain its limitations.

------------------------------------------------------------------------

## 7. Live Journey Map --- Planned Feature Specification

A live Google Maps-based journey view is a requested next implementation
step. It is **not confirmed as implemented in the inspected archive**;
the existing active journey screen was an analog instrument UI without a
Google Maps surface.

### Required behaviour

-   Show current GPS position, reference geometry, destination, and a
    compact ETA/confidence panel.
-   Synchronise the vehicle marker with the existing tracking stream; do
    not subscribe to location independently from the map.
-   Smooth marker movement and bearing changes without hiding real
    movement or deviations.
-   Provide camera-follow and manual recenter controls.
-   Support light/dark map styling consistent with the brand.
-   Handle permission denial, missing API key, map loading failure,
    stale GPS, and lifecycle transitions.
-   Choose a vehicle icon only from a confirmed transport mode; do not
    infer mode with unjustified certainty.
-   Make geometry provenance visible: verified route, estimated
    corridor, or unavailable.

### Transit geometry honesty

A road route returned for `DRIVE` is not a bus route or a train track.
Do not call a corridor "the actual route." For rail, use genuine rail
geometry only when an appropriate data source exists; otherwise label
the geometry as estimated or omit it.

### Dependency note

The inspected archive did not show Maps SDK/Maps Compose dependencies.
Confirm current dependency versions and API-key restrictions before
adding them.

------------------------------------------------------------------------

## 8. Route-Deviation Intelligence --- Required Specification

The requested deviation feature should extend the existing anomaly
system rather than create a second independent detector.

### States

-   **Normal:** observations are consistent with the reference geometry.
-   **Possible deviation:** early evidence exists but is not sufficient
    for a confirmed alert.
-   **Confirmed deviation:** sustained, accuracy-aware evidence exceeds
    configured thresholds.
-   **GPS stale:** no sufficiently fresh location observation is
    available.
-   **Geometry unavailable:** no usable reference route exists.

### Detection requirements

-   Measure geospatial distance to a polyline/segment, not merely
    distance from origin or destination.
-   Account for GPS accuracy and timestamp freshness.
-   Require multiple observations and/or sustained duration.
-   Use configurable thresholds, hysteresis, and recovery rules.
-   Avoid alerts for brief GPS drift, poor fixes, temporary urban-canyon
    errors, and ordinary stops.
-   Never call stale GPS or missing geometry a confirmed deviation.
-   Explain the state and its evidence to the passenger.
-   Centralise thresholds and test them against synthetic and recorded
    traces.

------------------------------------------------------------------------

## 9. Smart Ticket Import --- Required Specification

Ticket import is a requested feature and is **not confirmed as
implemented in the inspected archive**.

### Entry points

Add a `+` action on home and active journey screens. Offer: - PDF ticket
import; - image import; - paste/share ticket text.

Use Android document pickers and share intents. Do not request broad
storage access.

### Candidate fields

-   Origin and boarding point.
-   Destination and drop-off point.
-   Transport mode.
-   Operator and service/train/bus number.
-   Departure date and time.
-   Scheduled arrival date and time.
-   Intermediate stops.
-   PNR or booking reference, only if needed.

### Extraction workflow

1.  Read PDF text when possible.
2.  Use on-device OCR for images/scanned documents where feasible.
3.  Keep original text and extracted fields separate.
4.  Show confidence and ambiguity for each field where feasible.
5.  Never invent missing fields.
6.  Let the user edit and review the result.
7.  Require explicit confirmation before creating or replacing a
    journey.
8.  Preserve any existing active journey unless the user explicitly
    confirms replacement.
9.  Resolve locations and fetch route geometry only after confirmation
    and only where supported.
10. Persist only the confirmed fields required for the journey.

When extraction fails, preserve user-entered data and provide a clear
manual-entry path.

------------------------------------------------------------------------

## 10. Alarm, Background Execution and Reliability

The alarm is a core product capability, not a secondary notification.

### Requirements

-   Core alarm scheduling must work without a backend response.
-   Alarm updates must be idempotent and must not create duplicate
    pending alarms.
-   Reconcile persisted alarm intent after process recreation or reboot
    where supported.
-   Keep alarm state and journey state consistent.
-   Cancel journey-specific alarms when a journey is ended or deleted.
-   Surface exact-alarm permission, notification permission, sound
    settings, GPS state, and relevant battery restrictions in the
    readiness flow.
-   Explain when Android settings or manufacturer restrictions can
    affect delivery.
-   Do not claim that the app can override Do Not Disturb, silent mode,
    or every vendor restriction.
-   Test with the screen locked and app backgrounded on real devices.

------------------------------------------------------------------------

## 11. Explicit Privacy, Security and Retention Policy

This section is a **product requirement**. Do not claim that every rule
is already implemented until the code and tests demonstrate it.

Treat location history, route, itinerary, ticket images, OCR output,
PNR, and passenger details as sensitive personal data.

### Collection and processing

-   Collect only data necessary for the requested journey, ETA,
    deviation detection, and alarm.
-   Request location permission only when needed and explain why it is
    required.
-   Process ticket PDFs, images, and pasted text on-device wherever
    feasible.
-   Do not upload ticket files, OCR content, PNRs, or passenger details
    to external services by default.
-   Send only the minimum location/destination information required for
    a user-requested routing call.
-   Never use journey/ticket data for advertising, profiling, model
    training, or unrelated purposes.
-   Do not log precise coordinates, ticket contents, PNRs, tokens, or
    sensitive fields.
-   Keep API secrets out of source control. Restrict keys to the APIs
    and application contexts that need them.

### Retention defaults

-   **GPS:** Process updates in memory; persist only the latest fix and
    minimum state needed for recovery and journey calculations. No
    continuous location trail by default.
-   **Active journey:** Retain only while active and as long as needed
    for process-death recovery.
-   **Completed/cancelled journey:** Do not retain completed journey
    history by default.
-   **Imported source files:** Treat original ticket PDFs/images and OCR
    source text as temporary. Delete after successful extraction and
    confirmation, or on cancellation/failure cleanup.
-   **Ticket-derived fields:** Keep only confirmed fields necessary for
    the active journey. Avoid retaining PNR/passenger identifiers unless
    essential to a user-requested feature.
-   **Route geometry:** Keep only as long as required for the active
    journey unless the user explicitly opts into a disclosed feature
    that needs more retention.
-   **Alarms/notifications:** Retain only the state required to schedule
    and execute the current journey alarm. Clear journey-specific state
    when the journey is cancelled or deleted.
-   **Backups/logs/analytics:** Exclude sensitive journey and ticket
    data from cloud backup, debug logs, and analytics by default. Verify
    Android backup rules and diagnostic tooling.

### User controls and deletion

Provide controls to inspect, edit, cancel, and delete an active journey
and its ticket-derived data. Deletion must clear related Room records,
temporary files, cached route geometry, in-memory state, alarms, and
notifications. Cancelled or failed imports must not leave orphaned
files. Do not silently re-import or upload deleted data.

### Verification

Test import cancellation cleanup, successful import cleanup, journey
deletion, process-death recovery, and completion retention. Document any
data that cannot be deleted immediately and why. Do not claim complete
deletion, end-to-end encryption, or full offline operation unless
verified.

**Default principle:** Keep sensitive travel data on-device, retain the
minimum necessary information, and delete temporary and journey-specific
data when no longer needed.

------------------------------------------------------------------------

## 12. Visual Identity and Interaction Design

### Design concept

**Trust-First Analog Utility** --- a calm, precise instrument for travel
uncertainty, combining analog calibration cues with Swiss technical
layout discipline.

The interface should feel dependable and composed, not like a generic
fleet dashboard or a decorative night-themed app.

### Light theme

  Token          Value
  -------------- -----------
  Background     `#F7FAFC`
  Surface        `#FFFFFF`
  Primary text   `#17212B`
  Muted text     `#5B6B80`
  Navy           `#123B63`
  Deep navy      `#0C2A47`
  Amber          `#F4B942`
  Green          `#56A67A`
  Alert red      `#C95C5C`
  Border         `#DCE5ED`

### Dark theme

  Token          Value
  -------------- -----------
  Background     `#0A1522`
  Surface        `#0F2236`
  Primary text   `#E8EEF4`
  Muted text     `#9DB0C4`
  Navy           `#5E8DBD`
  Deep navy      `#0C2A47`
  Amber          `#F4B942`
  Green          `#56A67A`
  Alert red      `#E07A7A`
  Border         `#24405C`

### UI rules

-   Prioritise destination, ETA/range, confidence, and alarm readiness.
-   Use odometer-like digits and a restrained 180-degree gauge when they
    improve legibility.
-   Prefer crisp geometry, clear hierarchy, and strong contrast.
-   Avoid glow, random gradients, excessive animation, and moon clichés.
-   Avoid unnecessary vertical scrolling in the primary journey flow.
-   Never use colour alone for critical states; include labels/icons and
    accessible contrast.
-   Respect reduced-motion preferences and accessibility text scaling.
-   Map styling should complement, not overwhelm, the analog instrument
    UI.

------------------------------------------------------------------------

## 13. Failure States and User Communication

Every major subsystem needs explicit failure states.

  -----------------------------------------------------------------------
  Condition                           Required user-facing response
  ----------------------------------- -----------------------------------
  GPS permission denied               Explain what is unavailable and how
                                      to enable permission; do not
                                      pretend tracking is active.

  GPS stale/unavailable               Show last-fix age/status and
                                      degrade ETA confidence.

  Route geometry missing              Continue with clearly labelled
                                      fallback where possible; disable
                                      deviation confirmation.

  Route geometry estimated            Label it as estimated, not as the
                                      actual transit route.

  Network/API unavailable             Keep local tracking/alarm logic
                                      active; show reduced route/traffic
                                      capability.

  API key/configuration missing       Show a safe degraded state and
                                      developer-readable setup guidance;
                                      never crash the journey flow.

  Ticket extraction ambiguous         Ask the user to review/edit fields;
                                      do not auto-confirm.

  Alarm permissions/settings          Explain the limitation in readiness
  incomplete                          check.

  Journey cancelled/deleted           Stop tracking, cancel alarm, and
                                      clear associated data according to
                                      retention rules.
  -----------------------------------------------------------------------

------------------------------------------------------------------------

## 14. Testing and Evaluation

### Unit tests

-   Geospatial distance to route/polyline and invalid coordinates.
-   ETA fallback behaviour and uncertainty degradation.
-   Deviation state transitions, hysteresis, and recovery.
-   GPS stale watchdog.
-   Alarm reconciliation and duplicate prevention.
-   Ticket parsing, missing/ambiguous fields, and user corrections.
-   Data deletion and temporary-file cleanup.
-   Journey persistence and process-death restoration.

### Integration/device tests

-   Screen locked and app backgrounded.
-   Temporary network loss.
-   GPS loss and recovery.
-   Permission denial and later grant.
-   Process death and restoration.
-   Alarm delivery under documented device settings.
-   Journey cancellation while an alarm is scheduled.
-   Map initialization failure and missing key.
-   Ticket import from PDF, image, and shared text.

### Metrics

-   ETA MAE/median absolute error by journey stage.
-   Prediction-interval coverage and width where applicable.
-   Deviation precision/recall, false alerts per journey-hour, and
    detection delay.
-   Alarm firing success and trigger-to-alarm delay under defined
    conditions.
-   Battery consumption and recovery after GPS gaps.

Do not publish performance numbers without reproducible evidence. Label
simulated traces and scenarios as simulated.

------------------------------------------------------------------------

## 15. Roadmap and Priorities

### P0 --- Protect core reliability

1.  Reconcile the latest source with historical claims and establish a
    clean build/test baseline.
2.  Verify foreground tracking, watchdog, Room recovery, alarm
    scheduling, and cancellation on a physical device.
3.  Persist essential route provenance/geometry if required for active
    journey recovery.
4.  Audit privacy, backup, logging, temporary storage, and deletion
    behaviour.

### P1 --- Live journey map

1.  Integrate Maps SDK/Compose using compatible dependencies.
2.  Reuse the existing location stream and ViewModel state.
3.  Render route geometry with honest provenance labels.
4.  Add vehicle marker, bearing, camera follow/recenter, and stale-GPS
    states.
5.  Test permissions, lifecycle, API failure, and light/dark themes.

### P1 --- Route-deviation alerts

1.  Extend the existing anomaly detector.
2.  Implement accuracy-aware polyline distance and sustained
    confirmation.
3.  Add possible/confirmed/stale/no-geometry states.
4.  Add hysteresis, recovery, clear explanations, and tests.

### P2 --- Smart ticket import

1.  Add `+` entry points and picker/share flows.
2.  Implement local-first PDF text extraction and on-device OCR where
    feasible.
3.  Build editable review/confirmation UI.
4.  Connect confirmed fields to journey setup, route resolution, ETA,
    and alarm.
5.  Test privacy cleanup and active-journey replacement safeguards.

### P3 --- Validation and release

-   Build a reproducible test matrix.
-   Run real-device alarm and tracking tests.
-   Document setup/API-key restrictions.
-   Publish architecture, privacy, evaluation, screenshots, known
    limitations, and demo evidence.

------------------------------------------------------------------------

## 16. Definition of Done

A feature is complete only when: - It is integrated with the existing
source of truth and lifecycle. - It has correct loading, success,
degraded, and failure states. - It does not duplicate GPS/ETA/alarm
logic. - It handles process recreation and user cancellation
appropriately. - Sensitive data follows the retention rules. - Relevant
tests pass. - Build/test status is reported accurately. - UI claims
match actual data provenance and confidence.

For a complete NOCTURNE demo, a user must be able to configure a
journey, track with the screen locked under tested conditions, receive a
local adaptive alert, understand uncertainty or GPS failure, and cancel
the journey without leaving active alarms or unnecessary sensitive data.

------------------------------------------------------------------------

## 17. Decisions Log

### Decided

-   Product name: **NOCTURNE**.
-   Platform direction: native Android.
-   UI stack: Kotlin + Jetpack Compose.
-   Local persistence: Room.
-   Core system: GPS tracking + ETA + anomaly detection + local alarm.
-   Core alarm must not depend on backend availability.
-   Brand direction: Trust-First Analog Utility.
-   Privacy defaults: local-first processing, minimal retention, no
    default journey history, explicit deletion.
-   Route provenance must be honest; estimated road corridors are not
    actual transit routes.

### Planned but not verified as implemented

-   Google Maps-based live journey map and synchronized marker.
-   Explicit multi-state route-deviation UI and hardened confirmation
    logic.
-   PDF/image/share ticket import with local-first extraction/OCR.
-   Complete retention/deletion enforcement across database, files,
    caches, alarms, and notifications.
-   Route geometry persistence and provenance display.
-   Full test coverage for the above features.

### Must be re-verified

-   Current build and test status for the latest source archive.
-   Exact alarm and background location behaviour on real devices.
-   Current watchdog timing and thresholds.
-   Room migration/recovery correctness.
-   Google API key restrictions and billing/configuration.
-   Actual map SDK dependency state.
-   Backup exclusion and sensitive-data logging audit.

------------------------------------------------------------------------

## 18. Operating Rule for Future Development

This bible is the single source of truth for product intent and
engineering constraints, **not proof that every planned feature
exists**.

Before each major change: 1. Inspect the current repository. 2. Read
this bible. 3. Identify the existing implementation to extend. 4.
Implement the smallest coherent change. 5. Run available tests/build
checks. 6. Update this bible's implementation status, decisions, and
limitations.

Never silently replace existing architecture, invent test results,
mislabel estimated routes, or weaken privacy defaults. When
implementation reality differs from this document, verify the code and
update the document explicitly.
