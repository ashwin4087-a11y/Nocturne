# Privacy and data handling

NOCTURNE is intended to process journey information locally wherever practical. The policy below is a design target and must be checked against the implementation before release.

## Data categories

Potentially processed data includes:
- Device location while a journey is active.
- Origin, destination, route geometry, and ETA state.
- Alarm target and preferences.
- Text or images selected for ticket import.

## Principles

- Request location permission only when needed and explain why.
- Track location only during an active, user-visible journey.
- Keep journey records and ticket content on device by default.
- Do not log coordinates, ticket contents, booking references, or PNR data.
- Do not introduce analytics or third-party upload without explicit review and disclosure.
- Clean up temporary ticket-import files when processing completes or fails.
- Exclude sensitive local records from device backup where appropriate.
- Provide a clear way to stop tracking and remove stored journey data.

## Third-party providers

Geocoding, mapping, or routing requests may send the queried place names or coordinates to the selected provider. Document each provider, its purpose, retention practices, attribution requirements, and applicable usage limits before release.

## Important limitation

This file is a development guideline, not a legal privacy notice and not a claim that the current implementation satisfies every item. Review actual network calls, logs, backups, permissions, and storage before publishing.
