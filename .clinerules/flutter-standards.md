# Rule: Flutter / Dart Standards

**Priority: HIGH.**

## Stack (pinned)

- Flutter (stable channel), web first; iOS/Android later.
- Dart (stable, pinned via Flutter SDK).
- State management: **Riverpod** (strongly maintained).
- Routing: **go_router**.
- HTTP client: **Dio** (typed).
- Serialization: **Freezed** + **json_serializable**.
- Charts: **fl_chart** (MIT, actively maintained). Document any swap in an ADR.
- Secrets: **flutter_secure_storage** for mobile.
- Health platforms: platform channels or carefully reviewed plugins for
  HealthKit and Health Connect. Do not force shared Flutter code where native
  integration is required; create clean interfaces around native integrations.
- Versions pinned in `pubspec.yaml`. **No snapshots, milestones, release
  candidates, or abandoned packages.**

## Project layout (apps/flutter)

```
apps/flutter/
├── pubspec.yaml
├── lib/
│   ├── main.dart
│   ├── app/                 # app shell, router, theme
│   ├── core/                # shared core (network, errors, constants)
│   ├── features/
│   │   ├── readiness/
│   │   ├── sleep/
│   │   ├── training_load/
│   │   ├── activities/
│   │   ├── insights/
│   │   ├── coach/
│   │   ├── journal/
│   │   ├── import/
│   │   ├── privacy/
│   │   └── settings/
│   ├── data/                # repositories, api clients, dtos
│   └── shared/              # design system, widgets, extensions
├── test/                    # unit + widget tests
├── integration_test/        # integration tests
└── assets/
```

Feature-oriented structure. Each feature owns its widgets, providers,
and local models. Cross-feature access via shared repositories / contracts.

## Coding rules

- Null safety enforced.
- Immutable data classes via Freezed.
- Riverpod providers for all state; no `setState` outside trivial leaf widgets.
- Typed API client via Dio; generated DTOs from `packages/api-contracts`.
- All network calls go through a repository layer; no direct Dio calls in widgets.
- Responsive layouts: mobile, tablet, web breakpoints.
- Accessibility: semantic labels, sufficient contrast, keyboard navigation.
- Original visual language. **Do not imitate any vendor's visual design.**
- Secure storage for tokens/secrets on mobile; never hard-code secrets.
- No analytics/telemetry SDKs in the local edition.

## States to design for

Every data-driven screen must handle:
- Empty (no data yet)
- Loading
- Calibration (provisional state)
- Stale data (data-quality warning)
- Partial data
- Error
- Populated (normal)

## Testing

- Unit tests for logic and providers.
- Widget tests for critical screens.
- Golden tests for a limited number of stable critical screens.
- Integration tests for key flows.
- Responsive-layout tests.
- Accessibility checks.
- No real health data in any fixture.