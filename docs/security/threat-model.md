# Threat Model

> Security review is an independent gate. This document is a living artifact.

## System boundaries

- All services bind to `127.0.0.1` only.
- No inbound network exposure except loopback API.
- Raw files live outside the repo; only checksums/metadata in DB.

## Assets

- Health data (measurements, sleep, activities, journal).
- Credentials/secrets (local auth, future OAuth tokens).
- Derived metrics and insights.
- LLM outputs.

## Threats & mitigations

| Threat | Vector | Mitigation |
|---|---|---|
| Real health data committed to repo | Developer accident | `.gitignore`; CI health-data guard; PR attestation; no real data in fixtures |
| Secret leakage | Hard-coded secrets | No hard-coding; OS keychain; Gitleaks in CI |
| Prompt injection | Journal content | Sanitize; treat as untrusted; schema validation; fallback |
| LLM hallucination | Model invents metrics | Deterministic scores; LLM only explains; grounding tests |
| DB exposure | Postgres public bind | Loopback only; Spring Security; prepared statements |
| Ollama exposure | Ollama public bind | Loopback only |
| Import bombs | Malicious FIT/CSV/archive | File-size limits; archive-traversal defense; validation |
| CSV formula injection | Export CSV | Sanitize `=+-@` prefixes |
| Request-body logging | Health-data endpoints | Disable body logging; redact sensitive fields |
| Unauthorized access | No auth | Spring Security even locally; single-user auth; OIDC seam |
| Data loss | DB corruption | Backup/restore runbook; export capability |

## Abuse cases

- Importing a crafted FIT file to crash the parser → validation + size limits.
- Journal entry attempting prompt injection → untrusted-input treatment.
- Repeated import to exhaust storage → idempotency + content hashing.
- Exporting CSV with formula injection → sanitize cell prefixes.

## Review gate

Security review is represented as a versioned review artifact and a CI check,
not a fabricated GitHub approval. The repository owner is the human approval
authority.