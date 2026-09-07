# ADR-0004: Adopt the Flutter library stack

- Status: accepted
- Date: 2026-07-18
- Deciders: Principal Flutter Engineer (virtual), repository owner

## Context

The frontend must target web first, then iOS/Android, with optional desktop
later. We need strongly maintained state management, routing, typed HTTP,
serialization, charts, and secure storage. No analytics/telemetry SDKs in the
local edition.

## Decision

Adopt the following pinned Flutter stack:
- **Riverpod** for state management.
- **go_router** for routing.
- **Dio** (typed) for HTTP.
- **Freezed** + **json_serializable** for immutable data and serialization.
- **fl_chart** (MIT, actively maintained) for charts.
- **flutter_secure_storage** for mobile secrets.
- Platform channels or carefully reviewed plugins for HealthKit/Health Connect.

## Consequences

- **Pros**: all libraries are actively maintained and MIT/BSD/Apache licensed;
  feature-oriented structure; typed contracts from `packages/api-contracts`.
- **Cons**: Flutter web charting maturity is lower than JS; HealthKit/Health
  Connect may need native code.
- **Mitigations**: document any chart swap in an ADR; create clean interfaces
  around native integrations; no snapshots/milestones/RCs.

## Status update (2026-09-07): dropped Riverpod, Freezed, and json_serializable

The go_router/Dio/fl_chart/flutter_secure_storage choices above are
unchanged. Riverpod and the Freezed/json_serializable code-gen toolchain
were removed after an audit found zero actual usage anywhere in `lib/`: no
`@freezed`/`@JsonSerializable`/`@riverpod` annotation, no generated
`.g.dart`/`.freezed.dart` file, and not even a plain `Provider`/
`StateProvider`/`FutureProvider` declaration. Riverpod had been wired in
only as inert scaffolding (`ProviderScope` in `main.dart`,
`ConsumerStatefulWidget` on `DashboardPage`) that nothing ever actually
read from — every page in this app manages its own local `State` with
`setState` and parses JSON by hand instead, and always has. Left in place,
the declared dependencies misled a reader into assuming Riverpod-based
state management or Freezed data classes were this app's real pattern.

Removed `flutter_riverpod`, `riverpod_annotation`, `riverpod_generator`,
`freezed_annotation`, `freezed`, `json_annotation`, `json_serializable`, and
`build_runner` from `pubspec.yaml`; `DashboardPage` is now a plain
`StatefulWidget`. `flutter pub get` dropped 47 transitive dependencies.
`fl_chart` is unaffected — still genuinely unused today too, but kept as a
plausible future chart-widget candidate rather than abandoned scaffolding
(every trend view currently renders its own custom bars instead). See
`CHANGELOG.md`'s "Removed dead dependencies" entry for the verification
this was actually safe (`flutter analyze`/`test`/`build web` all clean
after removal).