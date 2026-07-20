# Rule: Privacy Standards

**Priority: CRITICAL — non-negotiable.**

## Local-first data flow

- All core functionality works locally. No health data is sent to an external
  LLM, analytics service, telemetry platform, error-reporting service, or
  third-party API unless the user **explicitly** enables it.
- Ollama binds to `127.0.0.1` only by default.
- No Google Analytics, Firebase Analytics, Sentry Cloud, Mixpanel, Amplitude,
  or similar hosted telemetry in the local edition.
- The application remains useful when the LLM is unavailable or disabled.

## Data minimization

- Store only what the product needs. Raw vendor payloads are retained in JSONB
  only when justified; explicit normalized columns are used for computation.
- Provenance on every imported or computed record (source, import batch,
  algorithm version).
- Idempotency keys and content hashes for imports.

## User rights

- Provide complete local data export.
- Provide complete local data deletion.
- Provide consent, export, and deletion flows in the UI.
- Never silently drop records; report unsupported/malformed records explicitly.

## Time & location

- UTC storage with original source time zone and local calendar context
  preserved.
- Handle daylight-saving transitions explicitly; no silent timezone coercion.

## LLM privacy

- The LLM receives only a constrained, structured summary — never unrestricted
  database access.
- Journal content is treated as prompt-injection-capable untrusted input.
- LLM output is validated against a schema; malformed output is rejected or
  safely regenerated.
- A deterministic template-based insight engine is the fallback so the app
  remains fully usable without an LLM.

## Prohibitions

- Do not include analytics/telemetry SDKs in the local edition.
- Do not log full request bodies for health-data endpoints.
- Do not place real health data in source control, test fixtures, logs,
  issues, PRs, or AI prompts. See `.clinerules/no-real-health-data.md`.
- Do not allow future cloud requirements to compromise local-only operation.