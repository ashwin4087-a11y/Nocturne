# NOCTURNE

<p align="center">
  <strong>A calmer way to travel long distances.</strong><br/>
  An Android-first transit companion built around honest ETAs, dependable journey tracking, and wake-up alerts you can trust.
</p>

<p align="center">
  <em>Know where you are. Understand what is uncertain. Wake up at the right time.</em>
</p>

---

## Why NOCTURNE?

Long-distance bus and train journeys are full of uncertainty: arrival times shift, stops happen unexpectedly, and it is easy to miss your destination while resting. NOCTURNE is designed to make that uncertainty easier to understand—not to hide it behind a falsely precise number.

## Product direction

- **Route-aware ETA** — estimate arrival using journey context and observed movement.
- **Uncertainty-aware estimates** — communicate a range and confidence rather than pretending every ETA is exact.
- **Journey monitoring** — track GPS freshness and surface possible route deviations carefully.
- **Adaptive wake-up alarm** — help travellers prepare before reaching their destination.
- **Offline-first principles** — keep core journey state on device and make connectivity limitations visible.
- **Privacy by design** — minimize data collection and avoid uploading ticket details or journey history by default.

> NOCTURNE is a work in progress. Feature descriptions communicate project goals; availability and reliability must be confirmed against the current source code and real-device testing.

## Design principles

1. **Honest over impressive.** Show when location, route geometry, or ETA quality is uncertain.
2. **Calm over noisy.** Keep the interface clear, restrained, and useful during a long journey.
3. **Local by default.** Prefer on-device journey state and explicit data handling.
4. **Graceful degradation.** A missing GPS fix or network route must produce a clear state—not fabricated certainty.
5. **Dependability over guarantees.** Background tracking and alarms must be validated across Android versions and device power-management settings.

## Tech stack

The Android application is being developed with Kotlin and Jetpack Compose, with Room for local persistence and Android location/background services for journey tracking. Routing and geocoding providers may be used where available; provider coverage, usage policies, network access, and route provenance should remain explicit.

## Repository status

This repository is being set up for the NOCTURNE project. The application source has not yet been committed here.

## Documentation

- [Project Bible](docs/PROJECT_BIBLE.md) — product intent, principles, and scope
- [Architecture](docs/ARCHITECTURE.md) — planned app layers and data flow
- [Privacy](docs/PRIVACY.md) — data minimization and privacy expectations
- [Field validation](docs/FIELD_VALIDATION.md) — testing checklist for real journeys

## Getting started

Application build instructions will be added when the Android source and Gradle wrapper are committed. Until then, this repository contains project documentation only.

## Roadmap

- [ ] Commit the Android application source and Gradle wrapper
- [ ] Document prerequisites and reproducible build steps
- [ ] Add automated CI for build and tests
- [ ] Add screenshots from the actual application
- [ ] Validate ETA calibration and deviation thresholds using real-world journeys
- [ ] Validate alarm and background-location behavior on supported devices
- [ ] Publish a privacy policy and provider attribution where required

## Contributing

Issues and thoughtful feedback are welcome. Before opening a pull request, describe the user problem, expected behavior, testing performed, and any privacy or battery implications. Avoid submitting secrets, personal journey histories, live ticket details, or real PNR data.

## License

No license has been added yet. Unless and until a license is included, reuse and redistribution are not granted by default.
