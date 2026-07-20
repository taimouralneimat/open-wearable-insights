# Personas

## Primary persona — "Taimour, the privacy-conscious athlete"

- **Device**: Garmin Forerunner-family watch.
- **History**: Previously used WHOOP; familiar with recovery/strain concepts.
- **Goals**: Understand recovery and training load from Garmin data with
  transparency; keep data local; get coaching grounded in real metrics.
- **Frustrations**: Opaque proprietary scores; cloud dependency; no easy way to
  ask "why is my readiness low today?" and get a grounded answer; vendor lock-in.
- **Environment**: Personal computer (macOS); comfortable with Docker and CLI.
- **Privacy stance**: Will not send health data to a cloud LLM or telemetry
  service. Wants local AI with a deterministic fallback.

## Secondary persona — "Maya, the data-curious runner"

- **Device**: Garmin watch; occasionally Apple Health.
- **Goals**: See trends, sleep quality, and behavioral correlations; understand
  what affects her sleep and recovery.
- **Frustrations**: Apps that show numbers without context; no correlation
  between behaviors (caffeine, late meals, travel) and outcomes.
- **Environment**: iPhone + web; less CLI-comfortable; wants a guided UI.
- **Privacy stance**: Prefers local-first; will use cloud only with explicit
  consent and clear benefit.

## Tertiary persona — "Sam, the open-source contributor"

- **Device**: Various; interested in the architecture.
- **Goals**: Extend the platform; add new ingestion adapters; evaluate the
  analytics methodology.
- **Frustrations**: Projects with no ADRs, no tests, no clear boundaries.
- **Environment**: Developer machine; values reproducible builds and CI.
- **Privacy stance**: Strict about no real health data in fixtures or PRs.

## Anti-persona (out of scope for MVP)

- **"Clinician using this for diagnosis"** — explicitly out of scope. This is a
  wellness/fitness product, not a medical device.
- **"Enterprise fleet operator"** — multi-tenant hosted use is a Phase 6
  consideration, not an MVP target.