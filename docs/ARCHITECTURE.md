# Architecture

## Intended application structure

The Android application is expected to use clear boundaries between UI, presentation state, domain logic, and persistence.

- **UI:** Jetpack Compose screens and reusable components.
- **ViewModels:** expose screen state and handle user actions.
- **Domain engines:** ETA calculations, geospatial helpers, deviation assessment, and alarm evaluation.
- **Repositories:** coordinate local storage and remote provider responses.
- **Services and receivers:** bounded background location updates and scheduled alarm handling.
- **Persistence:** Room for journey state and cached route data.

## Data flow

1. The user selects origin, destination, and travel mode.
2. The app resolves available route data and records its provenance.
3. The tracking service receives location updates and checks their freshness.
4. The ETA engine updates an estimate only when sufficient valid observations exist.
5. The deviation detector compares suitable location fixes against known route geometry.
6. The UI exposes status, uncertainty, and actions without hiding unavailable data.
7. The alarm state is persisted and scheduled through supported Android APIs.

## Reliability requirements

- Make route selection and journey start transitions explicit and testable.
- Reject invalid coordinates and stale or low-quality GPS observations.
- Avoid duplicate service starts and duplicate alarm scheduling.
- Keep blocking network operations off the main thread.
- Handle process recreation and permission revocation.
- Treat OS background restrictions as constraints, not something the app can override.

## Routing and geocoding

Provider adapters must isolate network formats from domain models. Preserve provider attribution and usage requirements. Never label a road route as rail geometry. If usable geometry is missing, mark route-based features unavailable or approximate instead of silently manufacturing a path.

## Testing

Test pure geospatial and ETA logic with unit tests. Test persistence migrations, journey lifecycle transitions, and alarm scheduling adapters. Validate background location, Doze, lock-screen behavior, and battery use on physical devices before making reliability claims.
