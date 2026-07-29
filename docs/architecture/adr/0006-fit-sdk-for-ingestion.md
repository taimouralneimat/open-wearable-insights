# ADR-0006: Use the Garmin FIT SDK for FIT file ingestion

- Status: accepted
- Date: 2026-07-18
- Deciders: Principal Backend Engineer (virtual), repository owner

## Context

The product must import Garmin FIT files as a first-class feature (not a hack).
We need an official, license-permissive SDK to parse FIT files reliably across
device types and firmware versions.

## Decision

Use the **Garmin FIT SDK** (Java, `com.garmin:fit` on Maven Central) for
parsing `.fit` files. Verify the exact license terms at implementation time
and record them in `NOTICE`. Only synthetic fixtures (`synthetic*.fit` under
`packages/test-data`) are used in development and testing.

SDK usage is isolated behind a `WearableConnector` interface in a new
`connections` module (`GarminFitConnector` is the first implementation),
rather than embedded directly in `ingestion` — this generalizes ADR-0005's
deferred `connections` module now that a second wearable vendor is a near-
term goal, per product direction: "whoop-like features with connectors,
starting with Garmin." Adding a future vendor means adding a new
`WearableConnector` bean; `ConnectorRegistry` and the ingestion pipeline
don't change.

## Consequences

- **Pros**: official SDK; reliable parsing; supports all FIT message types.
- **Cons**: SDK license must be verified and attributed; SDK updates may lag
  behind new device features.
- **Mitigations**: license recorded in `NOTICE`; SDK usage isolated behind
  `WearableConnector`; never commit real FIT files.
- FIT import remains a first-class feature, not a temporary hack.

## Status update (2026-07-29)

Implemented. `GarminFitConnector` extracts hr, steps, stress, sleep_stage,
and hrv (rMSSD from the RR-interval array) from real FIT message types via
the SDK's `Decode`/`MesgBroadcaster` API, and is wired into both
`DryRunValidator` (real record counts, not the old stubbed zero) and
`ImportService` (parsed measurements are now actually persisted to the
`measurements` table — previously no import format wrote to it at all).

`packages/test-data/synthetic-activity.fit` was upgraded from placeholder
text bytes to a real, parseable FIT binary, generated via the SDK's own
`FileEncoder` API (see `SyntheticFitFixtureGenerator` under
`services/api/src/test`) — still fully synthetic, no real device data.

Known limitation, not yet addressed: resting heart rate (`rhr`) is not
derived from FIT records — FIT exposes per-record instantaneous heart rate,
not a "resting" classification; deriving `rhr` needs additional logic (e.g.
minimum HR during a detected rest window) and is tracked as a follow-up.

## Status update (2026-07-29, part 2): activity session parsing

Extended `WearableConnector` with `parseActivities()` (default empty) and
added `ParsedActivity` alongside `ParsedMeasurement` — a session has a
start/end and aggregate stats (sport, distance, avg/max speed, avg/max HR,
calories, HR-zone breakdown), a fundamentally different shape from a
point-in-time reading, so it's a separate parse path rather than forcing it
into `ParsedMeasurement`.

`GarminFitConnector.parseActivities()` extracts this from FIT `SessionMesg`.
New `activities` table (Flyway V05), written by `ActivityRepository`
(mirrors `MeasurementRepository`) and wired into `ImportService`/
`DryRunValidator` alongside the existing measurement path. Read side:
`ActivitySessionRepository` + two new endpoints, `GET
/api/v1/activities/sessions` (list) and `/sessions/{id}` (detail) —
distinct from the pre-existing `/summary`/`/trends` endpoints, which are a
day-level step rollup, not a workout session.

Verified live: real session persisted from the fixture, both endpoints
return real data including a derived (not stored raw) pace figure, re-import
is idempotent. No Flutter screen consumes these endpoints yet — backend
only, tracked as a follow-up.