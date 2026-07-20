# Prompt & Output Contract

> The LLM receives a constrained, structured summary and must return a
> schema-validated structured output. Journal content is treated as
> prompt-injection-capable untrusted input.

## Input to the LLM (constrained summary)

The prompt includes **only**:
- Current readiness score (already computed deterministically).
- Factor contributions (names, values, directions).
- Recent baseline deviations.
- Sleep summary.
- Training-load summary.
- Data-quality status.
- Relevant journal entries selected by deterministic logic (sanitized).
- User goals and preferences.

The LLM **never** receives:
- Raw database access.
- Unrestricted health data.
- Credentials or tokens.

## Prompt-injection defense

- Journal content is sanitized before inclusion.
- Journal content is marked as untrusted in the prompt structure.
- The prompt explicitly instructs the model to treat journal text as data,
  not instructions.
- Output is validated against a schema; malformed output is rejected or
  safely regenerated.

## Required output schema

The model must return JSON matching:

```json
{
  "headline": "string (≤ 120 chars)",
  "summary": "string (≤ 500 chars)",
  "supportingFactors": [
    { "name": "string", "value": "string", "direction": "positive|negative|neutral" }
  ],
  "recommendedActions": ["string"],
  "confidence": "low|medium|high",
  "cautions": ["string"],
  "dataLimitations": ["string"]
}
```

## Validation

- Response is parsed and validated against the schema.
- If validation fails, the system attempts one safe regeneration.
- If regeneration fails, the **deterministic fallback** is used.
- All LLM outputs are stored in `llm_outputs` with `schema_valid` and
  `fallback_used` flags.

## Health-safety

- Output must not diagnose, recommend medications, or direct medical treatment.
- Cautions and data limitations are required fields.