# ADR-0003: Use Ollama + Spring AI for local LLM integration

- Status: accepted
- Date: 2026-07-18
- Deciders: Principal Solution Architect (virtual), Data & Applied-AI Engineer (virtual), repository owner

## Context

The product needs a local LLM to explain computed results and produce coaching
language. It must bind to loopback, be configurable, and degrade gracefully
when unavailable. The LLM must never invent scores; it only explains computed
results. A deterministic fallback is required.

## Decision

Use **Ollama** bound to `127.0.0.1` as the default local inference runtime, with
default model `qwen3:8b` (configurable via settings/env). Integrate via
**Spring AI 1.0.1** (GA). Provide configurable profiles (low-resource 4B,
default 8B, higher-quality 14B). Send only a constrained, structured summary
to the LLM. Validate output against a schema. Implement a deterministic
template-based fallback engine.

## Consequences

- **Pros**: local-first; Spring AI provides a clean abstraction; model is
  swappable; fallback ensures full usability without LLM.
- **Cons**: `qwen3:8b` needs ~8 GB RAM; 14B needs ~16 GB; quality varies by
  model; Spring AI is relatively new (but GA).
- **Mitigations**: default to 8B; make model configurable; never hard-code the
  model name; always validate output; always have the fallback.
- No snapshots/milestones/RCs: Spring AI 1.0.1 GA is used; 1.1.0-M1 is excluded.

## Status update (2026-09-07): dropped Spring AI, integrate via a direct RestClient

The Ollama/qwen3:8b/constrained-prompt/schema-validation/deterministic-fallback
decisions above are unchanged. What's superseded is specifically "integrate
via Spring AI 1.0.1": live testing (bringing up a real local Ollama server
and measuring real request timings, not just unit tests) found Spring AI
1.0.1's `OllamaOptions` has no field to express Ollama's own `"think": false`
request parameter — confirmed by inspecting the full field list of
`OllamaOptions.class` via `javap`. Without it, the default model's internal
"thinking" mode generated 900+ tokens of hidden reasoning before answering,
producing 18-45+ second response times and frequent timeouts for what should
be a one-sentence rephrase.

`LlmInsightService` now talks to Ollama's `/api/chat` REST endpoint directly
via a plain Spring `RestClient`, setting the real `think: false` JSON field
Spring AI couldn't express — this reliably brought response times to a
consistent ~4-7s. The now-fully-unused `spring-ai-ollama-spring-boot-starter`
dependency was removed from `build.gradle.kts`. See
`docs/product/parity-matrix.md` row 7 and `LlmInsightService`'s own class
Javadoc for the full investigation and live-verification results.