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