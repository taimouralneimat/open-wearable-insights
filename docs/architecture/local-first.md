# Local-First Architecture

> Local-first is the default, not a degraded mode. The application must remain
> fully useful when offline and when the LLM is unavailable.

## Principles

1. **All core functionality works locally.** Ingestion, normalization, storage,
   analytics, insights, and the deterministic fallback coach all run on
   loopback. No cloud dependency for core features.
2. **No health data leaves the machine** unless the user explicitly enables an
   optional outbound integration.
3. **No hosted telemetry.** No Google Analytics, Firebase, Sentry Cloud,
   Mixpanel, Amplitude, or similar in the local edition.
4. **Ollama binds to `127.0.0.1` only** by default.
5. **Useful without LLM.** A deterministic template-based insight engine is
   the fallback; the app is fully usable when Ollama is absent or disabled.
6. **Complete local export and deletion.** The user can export and delete all
   data locally at any time.

## Loopback binding

| Service | Default bind | Exposed publicly? |
|---|---|---|
| Spring Boot API | `127.0.0.1:8080` | No |
| PostgreSQL + TimescaleDB | `127.0.0.1:5432` | No |
| Ollama | `127.0.0.1:11434` | No |

## Data residency

- All health data stays on the local machine.
- Raw files live in a configurable local app-data directory **outside the repo**.
- Only checksums and metadata are in PostgreSQL.
- No health data is sent to an external LLM, analytics service, or third-party
  API unless the user explicitly enables it.

## LLM privacy

- The LLM receives only a **constrained, structured summary** — never
  unrestricted database access.
- Journal content is treated as prompt-injection-capable untrusted input.
- LLM output is validated against a schema; malformed output is rejected or
  safely regenerated.
- A deterministic fallback ensures full usability without the LLM.

## Evolution to hosted (Phase 6)

- Local-first operation is preserved even when a hosted option exists.
- Hosted features are opt-in and clearly separated.
- No future cloud requirement may compromise local-only operation.
- Multi-tenant isolation, OIDC, and cloud secrets are Phase 6 concerns.

## Verification

- No-network mode test: disconnect from the network; app remains usable.
- LLM-disabled mode test: stop Ollama; app remains usable via fallback.
- These are part of the system test suite (see
  [`docs/qa/test-strategy.md`](../qa/test-strategy.md)).