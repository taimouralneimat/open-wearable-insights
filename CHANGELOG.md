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

### Added — Phase 3: Insight intelligence (EC1/EC2)
- **Personalized rolling baselines**: `BaselineService` computes real
  per-metric baselines (HRV, RHR, stress, sleep duration, steps) from the
  user's own measurement history (28-day → 7-day → provisional window),
  with honest sample sizes and confidence — replaces the Phase 1 fixed
  provisional baseline. `ReadinessCalculator` gained a
  `calculate(CurrentMetrics, PersonalBaseline)` overload that computes
  deviations from the user's own history rather than pre-computed inputs.
  New `/api/v1/readiness/baseline` endpoint.
- **Score-diff and reasoning on tap**: `ScoreDiffService` computes a
  structured diff between today and a prior readiness score — which
  factors moved, by how much, sorted by impact. New
  `/api/v1/readiness/diff` endpoint. Flutter "vs Yesterday" card on the
  dashboard shows the score delta and per-factor changes.
- **Tests**: `PersonalBaselineTest` (5 tests), `ScoreDiffServiceTest`
  (4 tests).

### Added — Phase 3: Local coach "why" answering (EC3)
- `DeterministicInsightEngine.explainReadiness()` cites the actual ranked
  factor contributions behind the score — never generic advice — and
  explains what changed vs. a real prior day when one exists, or says so
  explicitly when it doesn't. Works fully via the deterministic fallback,
  no LLM required.
- New `GET /api/v1/coach/why` endpoint. Flutter: "Why?" button on the
  coach card expands to show the answer and cited metrics.
- Fixed an additional gap found while implementing this: `CoachController
  .getInsight()` was still using Phase 1's hardcoded synthetic inputs,
  never upgraded when EC1 added real personalized baselines — now uses
  the same real baseline/current-metrics pipeline as `/latest`.
- **Tests**: 4 new tests on `DeterministicInsightEngineTest`.

### Added — Phase 3: Confidence & limitation displays on all surfaces (EC4)
- Audit found `SleepController`, `ActivitiesController`, and
  `DataQualityController` had zero confidence/limitation disclosure —
  100% hardcoded placeholder data with no warning it wasn't real. Added
  explicit `confidence`/`limitations` fields to all three, and to
  `ScoreDiff` (which previously had no confidence field at all).
- **Flutter**: new shared `ConfidenceBanner` widget
  (`lib/widgets/confidence_banner.dart`) wired into the Sleep, Activities,
  and Data Quality pages, so the disclosure is actually visible to users
  instead of sitting unused in an API response.

### Added — Phase 3: Real sleep/training-load insights (EC5)
- New `sleep/domain` + `sleep/application` (`SleepInsightService`)
  computes real per-night sleep summaries from `sleep_stage`
  measurements (stage encoding matches
  `packages/test-data/synthetic_generator.py`), with an original v0.1
  sleep-score formula and honest confidence based on reading density.
- New `activities/domain` + `activities/application`
  (`ActivityInsightService`) computes real step counts from measurement
  data. Calories/active minutes/active zone minutes are reported as
  `null` rather than fabricated — not yet tracked in the data model.
  Both surfaces use "most recent day with data" instead of strict
  calendar "today".
- **Verified end-to-end** against real data: hand-checked the sleep
  score formula matched the API response exactly; activity
  summary/trends cross-checked against direct SQL and matched exactly.

### Added — Phase 3: Journal & behaviors taxonomy (EC6)
- New `journal/domain` + `journal/application` (`JournalService`) +
  `journal/adapter/in` (`JournalController`) on top of the
  `journal_entries` table that existed since the Phase 0 schema but was
  never built on.
- Taxonomy: 6 categories, 33 behaviors — deliberately broader than the
  original Phase 1 stub (6 behaviors, no categories), suggested rather
  than enforced (free-text custom behaviors still accepted). Entries
  always stored `treated_as_untrusted = true`.
- New `GET /api/v1/journal/behaviors`, `POST /api/v1/journal/entries`,
  `GET /api/v1/journal/entries` endpoints.
- **Flutter**: new `journal_page.dart` with a category/behavior picker
  and entry list, wired into dashboard navigation.
- **Tests**: 6 new `JournalServiceTest` cases.
- **Verified end-to-end**: real POST/GET round-trip against the running
  backend, raw DB row inspection confirming correct encoding, blank-
  behavior validation confirmed rejected with 400.
- **Known gap**: the Flutter journal page was verified via
  `flutter analyze`/`build web` only, not an interactive click-through —
  noted explicitly in `docs/qa/phase-gate-report-phase3.md` rather than
  claimed as fully covered.

### Added — Phase 3: Exploratory behavior correlations (EC7)
- New `journal/domain/BehaviorCorrelation.java` +
  `journal/application/CorrelationService.java` — compares readiness
  scores on days a logged behavior was present vs. absent. Deliberately
  a simple group-mean comparison, not dressed up with statistics (e.g.
  p-values) a handful of data points can't support.
- Requires a minimum sample size (≥3) in both groups — behaviors below
  it are excluded, never shown with fabricated confidence. Confidence is
  only ever "low"/"medium", never "high", by design. Every result always
  carries explicit correlation-not-causation language.
- Reused `ReadinessScoreHistoryRepository` (added a
  `findScoresByAccountId()` method) rather than duplicating that query
  via raw SQL from the journal module.
- New `GET /api/v1/journal/correlations` endpoint. 6 new
  `CorrelationServiceTest` cases, including one that encodes "confidence
  is never high" as an executable assertion. Flutter: new
  `_CorrelationsCard` on the journal page.
- **Verified end-to-end**: seeded real multi-day readiness + journal
  data, hand-traced the exact group-mean math against the live API
  response (matched exactly, including data left over from earlier in
  this session's own EC6 testing), confirmed a below-threshold behavior
  was correctly excluded.

### Phase 3 complete
All 7 exit criteria addressed (EC6 has one documented coverage gap — see
docs/qa/phase-gate-report-phase3.md). Every exit criterion surfaced at
least one real bug or gap during independent verification that a
build-passing check alone would have missed, including in this session's
own commits, not just Cline's.

### Fixed — Phase 3 bugs found during quality gate review
- **Bug 1 (training load silently broken)**: `CurrentMetricsService
  .fetchStepsSum()` had leftover dead code — a query using
  `GROUP BY DATE(time)` through `jdbcTemplate.queryForObject()`, which
  requires exactly one row. With more than one day of step data (the
  normal case), it threw `IncorrectResultSizeDataAccessException`,
  silently caught, always returning 0 — Training load (ACWR) was reported
  as "missing" even when real data existed. Fixed by deleting the dead
  query.
- **Bug 2 (sleep-duration approximation fed nonsense into the score)**:
  the `readingCount * 0.25 hours` approximation produced clearly
  unrealistic values (e.g. 1.0 hours) against real test data, directly
  corrupting the score's largest negative factor. Fixed by adding a
  plausibility bound (2-14 hours) in both `CurrentMetricsService` and
  `BaselineService` — values outside that range are logged and reported
  as missing data rather than fed into the score.
- **Bug 3 ("vs Yesterday" never compared against yesterday)**:
  `ReadinessController.diff()` fabricated "prior" by plugging baseline
  averages back in as if they were yesterday's raw metrics, which
  mathematically forced every prior-day deviation to exactly 0. Fixed by
  adding real persistence: new `readiness_score_history` table (Flyway
  V04), `ReadinessScoreHistoryRepository`. `/diff` now looks up the real
  persisted prior day; if none exists yet, returns an honest
  `comparedAgainst: "no_prior_data"` response instead of fabricating a
  comparison. `ScoreDiff` gained a `comparedAgainst` field; the Flutter
  card only shows "vs Yesterday" and the trend delta when real prior-day
  data exists.
- **Contributing factor**: every DB query in `BaselineService` and
  `CurrentMetricsService` silently swallowed exceptions with no logging —
  exactly why Bug 1 went undetected through the original end-to-end
  verification pass. Replaced every `catch (Exception e) {}` with
  `log.warn(...)` including the exception.

### Notes
- No real health data is used in development or testing. All fixtures are
  synthetic or explicitly anonymized.
- The application remains fully usable when Ollama is unavailable.
- All backend changes verified with `./gradlew build` + tests.
- All Flutter changes verified with `flutter analyze` + `flutter build web`.
- Phase 3 bug fixes were additionally verified against the real running
  backend + Postgres with real inserted data, including reproducing Bug 1
  directly in SQL before fixing it, per the process rules in
  `docs/qa/phase1-fixes-and-lessons.md`.

[Unreleased]: https://github.com/taimour-dev/open-wearable-insights/compare/HEAD