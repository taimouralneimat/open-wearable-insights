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

### Added — Phase 2: Personal Garmin import
- **Ingestion module**: local import directory scanner (outside repo),
  dry-run validator with SHA-256 content hashing, duplicate detection,
  unsupported-record reporting, import service with progress reporting,
  explicit errors, and undo support.
- **JPA persistence**: `ImportBatch` entity, `ImportBatchRepository`,
  Flyway V02 migration (`file_name`, `record_count` columns),
  Flyway V03 migration (seeds default local account for single-user mode,
  per ADR-0008).
- **Per-file transaction isolation**: each file's persist runs in its own
  `REQUIRES_NEW` transaction via `TransactionTemplate`, so one bad file
  can't poison the session for the rest of the batch.
- **REST endpoints**: `/api/v1/ingestion/scan`, `/dry-run`, `/import`,
  `/undo/{batchId}`, `/batches`.
- **Sleep module**: `/api/v1/sleep/summary` (stage breakdown),
  `/api/v1/sleep/trends` (7-day trend).
- **Activities module**: `/api/v1/activities/summary`,
  `/api/v1/activities/trends` (7-day trend).
- **Data-quality dashboard**: `/api/v1/data-quality/summary` with
  completeness, freshness, per-metric coverage, and quality issues.
- **Flutter UI**: import page (guided UI with dry-run validation, import
  progress, undo, batch history), sleep page (score, stages, trends),
  activities page (summary, steps trend), data-quality page (completeness,
  coverage, issues). All pages handle empty/loading/error/populated states.
- **Navigation**: dashboard AppBar with sleep, activities, data-quality,
  and import buttons.
- **Tests**: `DryRunValidatorTest` (9 tests: hash correctness, duplicate
  detection, unsupported-record reporting, JSON/CSV parsing, FIT recognition),
  `ImportServiceTest` (8 tests: successful import, duplicate skip, undo,
  per-file error isolation, mixed files, empty/nonexistent directory).

### Fixed — Phase 2 bugs found during quality gate review (round 1)
- **Bug 1 (FK violation)**: `ImportService` hardcoded `DEFAULT_ACCOUNT_ID = 1L`
  but the `accounts` table was never seeded — no row with id 1 existed. Every
  `import_batches` insert violated the `account_id` FK and 500'd. Fixed by
  adding Flyway V03 migration that seeds the default local account using
  `OVERRIDING SYSTEM VALUE` (required because `accounts.id` is
  `GENERATED ALWAYS AS IDENTITY`).
- **Bug 2 (transaction poisoning)**: the entire per-file import loop ran
  inside one `@Transactional` method. When the first insert failed, Spring
  marked the transaction rollback-only, poisoning the session for every
  subsequent file. Fixed by giving each file's persist its own transaction
  via `TransactionTemplate` with `PROPAGATION_REQUIRES_NEW`.

### Fixed — Phase 2 bugs found during quality gate review (round 2)
- **Bug 3 (dry-run doesn't check DB for duplicates)**: `DryRunValidator` only
  detected duplicates within a single scan (two files in the same folder with
  the same content) — it never checked against previously-imported DB records.
  The preview and the real import disagreed. Fixed by injecting
  `ImportBatchRepository` into `DryRunValidator` and checking
  `existsByContentHashAndStatus(hash, "imported")` — the same check
  `ImportService` uses — so the preview and import always agree.
- **Bug 4 (undo doesn't restore re-importability)**:
  `ImportBatchRepository.existsByContentHash()` checked for any row with that
  hash regardless of status, so once a batch was undone, its file was
  permanently blocked from re-import. Fixed by replacing with
  `existsByContentHashAndStatus(hash, "imported")` — undone batches no longer
  block re-import. Added `undoThenReimport_succeeds` test to verify.

### Notes
- No real health data is used in development or testing. All fixtures are
  synthetic or explicitly anonymized.
- The application remains fully usable when Ollama is unavailable.
- All backend changes verified with `./gradlew build` + tests.
- All Flutter changes verified with `flutter analyze` + `flutter build web`.

[Unreleased]: https://github.com/taimour-dev/open-wearable-insights/compare/HEAD