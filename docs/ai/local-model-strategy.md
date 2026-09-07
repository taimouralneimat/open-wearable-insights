# Local Model Strategy

> Ollama bound to `127.0.0.1`. Default model `qwen3:8b`. Configurable via
> settings/env. App remains fully usable without the LLM.

## Runtime

- **Ollama** bound to `127.0.0.1:11434` only.
- Integrated via a direct Spring `RestClient` call to Ollama's `/api/chat`
  REST endpoint, not Spring AI — see ADR-0003's 2026-09-07 status update for
  why (Spring AI 1.0.1's `OllamaOptions` can't express Ollama's `think: false`
  request field, which the default model needs to avoid 18-45+s response
  times).
- Model name is **never hard-coded**; configured via `application.yml` and
  environment variables.

## Model profiles

| Profile | Model | RAM (approx) | Use case |
|---|---|---|---|
| Low-resource | `qwen3:4b` | ~4 GB | Constrained machines |
| Default | `qwen3:8b` | ~8 GB | Balanced quality/speed |
| Higher-quality | `qwen3:14b` | ~16 GB | Best local quality |

- Profiles are illustrative sizing guidance, not a built feature: there is no
  Settings-page model picker today. The model is set once via
  `owi.llm.model` (`application.yml`/env var) — see `LlmInsightService`. The
  Privacy page shows live, read-only LLM status (enabled/available), not a
  selector.
- The app detects Ollama availability and degrades gracefully.

## Privacy

- The LLM receives only a **constrained, structured summary** — never
  unrestricted database access.
- No health data leaves the machine.
- Journal content is treated as prompt-injection-capable untrusted input.

## Fallback

- A **deterministic template-based insight engine** is the fallback.
- If Ollama is unavailable or output is malformed, the fallback is used.
- The app is fully usable without the LLM (scores, factors, insights, export).

## Versioning

- No Spring AI dependency; a plain Spring `RestClient` is used instead (see
  ADR-0003's status update).
- Model swaps require a compatibility check and an ADR.