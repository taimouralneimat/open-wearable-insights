# Local Model Strategy

> Ollama bound to `127.0.0.1`. Default model `qwen3:8b`. Configurable via
> settings/env. App remains fully usable without the LLM.

## Runtime

- **Ollama** bound to `127.0.0.1:11434` only.
- Integrated via **Spring AI 1.0.1** (GA).
- Model name is **never hard-coded**; configured via `application.yml` and
  environment variables.

## Model profiles

| Profile | Model | RAM (approx) | Use case |
|---|---|---|---|
| Low-resource | `qwen3:4b` | ~4 GB | Constrained machines |
| Default | `qwen3:8b` | ~8 GB | Balanced quality/speed |
| Higher-quality | `qwen3:14b` | ~16 GB | Best local quality |

- Profiles are user-selectable in Settings.
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

- Spring AI 1.0.1 GA is used. No snapshots, milestones, or RCs.
- Model swaps require a compatibility check and an ADR.