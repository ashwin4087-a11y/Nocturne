# NOCTURNE Project Bible

## Product thesis

NOCTURNE is an Android-first companion for long-distance bus and train travel. Its purpose is to reduce journey uncertainty by explaining estimated arrival, tracking freshness, possible deviations, and wake-up timing in a calm and transparent interface.

## Who it serves

Travellers who want to rest during long journeys without losing awareness of their progress or destination.

## Product principles

- Tell the truth about uncertainty.
- Never fabricate route geometry, live service status, or ETA confidence.
- Distinguish missing, stale, approximate, and verified information.
- Keep sensitive journey data on device by default.
- Treat battery use, permissions, and background behavior as product features.
- Make the most important journey state understandable at a glance.

## Core capabilities (intended)

- Route-aware ETA with an interval rather than false precision.
- GPS journey tracking with explicit stale-location handling.
- Cautious route-deviation detection.
- Destination-aware wake-up alarm.
- Offline-friendly local state and route-cache behavior.
- Ticket information import with temporary-file cleanup and no unnecessary upload.

## Visual identity

Use a restrained, instrument-inspired interface: precise typography, clear status labels, generous spacing, and no decorative effects that obscure information.

Logo palette:
- Primary navy: `#123B63`
- Deep navy: `#0C2A47`
- Signal amber: `#F4B942`
- Light background: `#F7FAFC`
- Light text on dark: `#E8EEF4`

Use amber for a meaningful signal, not for every interactive element. Preserve the supplied logo assets rather than redrawing or recoloring them casually.

## Non-goals

- Claiming official transit operator data when none is integrated.
- Presenting road-routing geometry as verified rail track geometry.
- Guaranteeing arrival times or alarm delivery under every Android power policy.
- Uploading tickets or journey history without a clear, informed reason.

## Project maturity

This document describes intent and design constraints. It is not proof that every capability is implemented or field validated.
