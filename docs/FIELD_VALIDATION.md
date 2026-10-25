# Field validation checklist

Use this checklist before describing NOCTURNE as reliable for real journeys. Record device model, Android version, app build, journey mode, date, and observed limitations—without recording personal ticket identifiers.

## ETA quality

- [ ] Compare predicted arrival intervals with actual arrival times over multiple journeys.
- [ ] Measure how often actual arrival falls inside the stated interval.
- [ ] Check behavior during sustained movement, traffic, station stops, and sparse GPS.
- [ ] Ensure insufficient samples produce a visibly low-confidence or unavailable state.
- [ ] Do not claim calibrated confidence until supported by a representative dataset.

## GPS and route deviation

- [ ] Test poor accuracy, stale fixes, permission revocation, and temporary GPS loss.
- [ ] Test parallel roads, stations, terminals, and legitimate detours.
- [ ] Confirm deviation warnings require suitable fixes and do not fire on stale data.
- [ ] Confirm recovery and unavailable states are clear to the traveller.
- [ ] Confirm route source and geometry mode are visible where material.

## Alarm and background operation

- [ ] Test screen off, app backgrounded, process recreation, and device reboot.
- [ ] Test Doze, battery saver, exact-alarm permission, notification permission, and DND.
- [ ] Test lock-screen behavior and full-screen-intent restrictions where applicable.
- [ ] Confirm the user can stop or cancel the alarm.
- [ ] Never promise that Android will deliver an alarm under every device or user setting.

## Battery and privacy

- [ ] Measure battery consumption over a representative multi-hour journey.
- [ ] Verify tracking stops when the user ends a journey.
- [ ] Inspect logs for coordinates, ticket content, and booking identifiers.
- [ ] Verify temporary import files are deleted after success, failure, and cancellation.
- [ ] Verify backup exclusions and Room migrations.

## Reporting results

Separate automated test results from field observations. Include sample size, device/OS versions, known limitations, and failures. Do not use a single successful test to claim general reliability.
