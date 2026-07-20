# Rule: Documentation Standards

**Priority: HIGH.**

## Required documentation

Maintain the following documents throughout the project lifecycle:

- `README.md` — project overview, quick start, principles.
- `CONTRIBUTING.md` — contribution standards and workflow.
- `SECURITY.md` — vulnerability reporting and security commitments.
- `CODE_OF_CONDUCT.md` — community standards.
- `CHANGELOG.md` — notable changes per release (Keep a Changelog format).
- `NOTICE` — third-party attributions and license inventory.

## Product documentation (`docs/product/`)

- `vision.md` — product vision and principles.
- `personas.md` — target personas.
- `user-journeys.md` — primary user journeys.
- `parity-matrix.md` — competitive parity matrix (updated every relevant release).
- `story-map.md` — prioritized story map.
- `backlog.md` — executable backlog with acceptance criteria.
- `release-plan.md` — phased release plan.

## UX documentation (`docs/ux/`)

- `information-architecture.md` — IA and dashboard hierarchy.
- `wireframes/` — low-fidelity wireframes (Mermaid/ASCII specs, not screenshots).

## Architecture documentation (`docs/architecture/`)

- `system-context.md` — system context diagram.
- `container-view.md` — container view.
- `component-view.md` — component view.
- `data-model.md` — canonical data model.
- `data-provenance.md` — provenance model.
- `local-first.md` — local-first architecture.
- `adr/` — Architecture Decision Records (sequential: 0001, 0002, …).

## Analytics documentation (`docs/analytics/`)

- `readiness-methodology.md` — readiness algorithm methodology.
- `sleep-methodology.md` — sleep analysis methodology.
- `training-load-methodology.md` — training-load methodology.
- `correlation-methodology.md` — correlation analysis methodology.

## AI documentation (`docs/ai/`)

- `local-model-strategy.md` — local model selection and configuration.
- `prompt-and-output-contract.md` — constrained prompt and structured output.
- `evaluation.md` — LLM evaluation and hallucination/safety tests.

## Security documentation (`docs/security/`)

- `threat-model.md` — threat model and abuse cases.
- `privacy-model.md` — privacy model.
- `release-review.md` — security release review (versioned).

## QA documentation (`docs/qa/`)

- `test-strategy.md` — test strategy and coverage.
- `release-report.md` — QA release report (versioned).

## Runbooks (`docs/runbooks/`)

- `local-development.md` — local development setup.
- `importing-data.md` — importing data (local folder outside repo).
- `backup-and-restore.md` — backup and restore procedures.

## Diagrams

- Use **Mermaid** or **PlantUML** source, not screenshots alone.
- Store diagrams under `docs/`.
- Keep diagrams in sync with code; update when architecture changes.

## ADR template

```markdown
# ADR-NNNN: Title

- Status: proposed | accepted | superseded by ADR-XXXX
- Date: YYYY-MM-DD
- Deciders: Virtual role(s) and human approver

## Context

(Why is this decision needed? What are the forces?)

## Decision

(What is the change?)

## Consequences

(What are the trade-offs, risks, and follow-ups?)
```

## Update cadence

- Update `parity-matrix.md` during every relevant release.
- Update `CHANGELOG.md` for every notable change.
- Add an ADR for any change affecting module boundaries, data model, security,
  privacy, AI integration, or external contracts.
- Update runbooks when local setup or import procedures change.