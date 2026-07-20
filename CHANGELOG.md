# Changelog

All notable changes to Open Wearable Insights are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
for releases after 1.0.0. Pre-1.0 development versions use `0.<phase>.<increment>`.

## [Unreleased]

### Added — Phase 0: Discovery & foundation
- Repository bootstrap: `.gitignore`, `LICENSE` (Apache 2.0), `NOTICE`,
  `README.md`, `SECURITY.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`,
  `CHANGELOG.md`.
- Product documentation: `docs/product/vision.md`, `personas.md`,
  `user-journeys.md`, `parity-matrix.md`, `story-map.md`, `backlog.md`,
  `release-plan.md`.
- UX documentation: `docs/ux/information-architecture.md`, `wireframes/`.
- Architecture documentation: `system-context.md`, `container-view.md`,
  `component-view.md`, `data-model.md`, `data-provenance.md`, `local-first.md`,
  and ADRs under `docs/architecture/adr/`.
- Analytics methodology: `readiness-methodology.md`, `sleep-methodology.md`,
  `training-load-methodology.md`, `correlation-methodology.md`.
- AI documentation: `local-model-strategy.md`, `prompt-and-output-contract.md`,
  `evaluation.md`.
- Security documentation: `threat-model.md`, `privacy-model.md`.
- QA documentation: `test-strategy.md`.
- Runbooks: `local-development.md`, `importing-data.md`, `backup-and-restore.md`.
- Cline rules under `.clinerules/`.
- GitHub templates: PR template, issue templates, Dependabot, CI skeleton.
- Local infrastructure: `infrastructure/docker/docker-compose.yml`
  (PostgreSQL + TimescaleDB, Ollama bound to loopback).

### Added — Phase 1: Local vertical slice (in progress)
- `services/api` Spring Boot modular monolith (Java 21, Spring Boot 3.5.6,
  Spring Modulith, Spring AI 1.0.1, Flyway, Testcontainers, OpenAPI 3,
  Micrometer, Spring Security).
- `apps/flutter` Flutter client (Riverpod, go_router, Dio, Freezed,
  fl_chart, flutter_secure_storage).
- `packages/api-contracts` shared OpenAPI/DTOs.
- `packages/test-data` synthetic health-data generator and synthetic FIT fixture.
- Ingestion → normalization → provisional readiness score → deterministic
  insight engine → optional Ollama explanation with fallback.
- Automated tests: unit, module-boundary, repository integration, import-parser,
  idempotency, golden analytics, API contract, LLM schema/fallback.

### Notes
- No real health data is used in development or testing. All fixtures are
  synthetic or explicitly anonymized.
- The application remains fully usable when Ollama is unavailable.

[Unreleased]: https://github.com/taimour-dev/open-wearable-insights/compare/HEAD