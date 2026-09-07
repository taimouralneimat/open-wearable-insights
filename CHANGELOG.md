# Changelog

All notable changes to Open Wearable Insights are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
for releases after 1.0.0. Pre-1.0 development versions use `0.<phase>.<increment>`.

## [Unreleased]

### Correction (2026-07-30): "Phase 4" labels below are a misnomer
Several entries below are headed "Phase 4," but `docs/product/release-plan.md`
defines Phase 4 as **mobile applications** (iOS/Android builds, HealthKit
adapter, Health Connect adapter) — none of that work has started. What was
actually built (pluggable wearable connectors, real Garmin FIT parsing,
activity-session parsing, the Flutter design-system pass, and the activity
session list/detail UI) was really **Phase 2 hardening** — release-plan.md's
Phase 2 exit criteria explicitly call for "Sleep, activity, trend views" and
"Dry-run validation," which this work delivered for real in place of the
prior stubs — plus general parity-gap-closing (closing parity-matrix row
#10). The existing entry headers below are left as originally written, since
they're the historical record of what was actually written at the time.

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

### Added — Phase 4: Pluggable wearable connectors, real FIT parsing (ADR-0006)
- New `connections` module: `WearableConnector` interface + `ConnectorRegistry`
  (Spring autowires every registered connector) — adding a future wearable
  vendor means adding one class, not touching `ingestion`.
- `GarminFitConnector`: first implementation, using the official Garmin FIT
  SDK (`com.garmin:fit`, Maven Central) to extract hr, steps, stress,
  sleep_stage, and hrv (rMSSD from the RR-interval array) from real FIT
  message types.
- Wired the connector into `DryRunValidator` (real record counts, replacing
  the previous stubbed `yield 0`) and `ImportService`, which now actually
  persists parsed measurements to the `measurements` table via the new
  `MeasurementRepository` — previously **no import format, including
  JSON/CSV, ever wrote to `measurements`**; import only ever recorded batch
  metadata. FIT files were the forcing function that surfaced this.
- `packages/test-data/synthetic-activity.fit` upgraded from placeholder text
  bytes to a real, parseable FIT binary built with the SDK's own encoder
  (`SyntheticFitFixtureGenerator`, backend test sources) — still entirely
  synthetic, no real device data, per the project's no-real-health-data rule.
- Verified live end-to-end against a running backend: dry-run reports the
  real record count, import persists real rows (confirmed via direct DB
  query joined through `provenance` → `import_batches`), re-import is
  idempotent (no duplicate rows), and a corrupted `.fit` file reports a real
  parse error instead of silently returning 0 or crashing the batch scan.
- **Known gap**: `rhr` (resting heart rate) is not derived — FIT exposes
  per-record instantaneous HR only; tracked as a follow-up.

### Added — Phase 4: Activity session parsing (parity-matrix row 10)
- `WearableConnector.parseActivities()` (default empty) + new
  `ParsedActivity` domain type — discrete workout sessions (sport, duration,
  distance, avg/max speed, avg/max HR, calories, HR-zone breakdown) are a
  different shape from point-in-time measurements, so they get their own
  parse path rather than being forced into `ParsedMeasurement`.
- `GarminFitConnector.parseActivities()` extracts this from FIT `SessionMesg`.
- New `activities` table (Flyway V05) + `ActivityRepository` (write side,
  mirrors `MeasurementRepository`), wired into `ImportService`/
  `DryRunValidator` alongside the existing measurement path.
- New read side `ActivitySessionRepository` and two endpoints: `GET
  /api/v1/activities/sessions` (list, up to 20 most recent) and
  `/sessions/{id}` (detail, 404 if not found) — distinct from the
  pre-existing `/summary`/`/trends`, which are a day-level step rollup, not
  a workout session. Detail response includes a derived (not stored raw)
  pace figure computed from average speed.
- Fixture generator extended with a synthetic 30-minute running session;
  `synthetic-activity.fit` regenerated.
- Verified live end-to-end: real session persisted (confirmed via direct DB
  query), both endpoints return real data, re-import is idempotent (no
  duplicate activity rows).
- **Known gap**: only whole-session summaries are parsed (FIT `SessionMesg`)
  — lap/split-level detail (`LapMesg`) is not yet extracted. No Flutter
  screen consumes these endpoints yet; this PR is backend-only.

### Fixed — Garmin FIT SDK dependency coordinates
- `libs.versions.toml`/`build.gradle.kts` referenced the FIT SDK via
  `com.github.garmin:fit-java-sdk` through JitPack, which doesn't resolve.
  The real, official artifact is `com.garmin:fit` on Maven Central. Fixed
  and verified via `./gradlew dependencies`.

### Added — Personal Records / Milestones (original delight feature, not parity)
- New `milestones` module: a small, honest, celebratory surface that tells
  the user when they've genuinely hit a new personal best on something this
  app already tracks — not a competitor-parity row, an original addition.
  Every milestone is a real comparison between the account's own current
  computed value and its own real historical values; an honest empty list
  is returned when nothing genuinely new happened, never fabricated filler.
  No LLM involvement — plain deterministic templating, same convention as
  `DeterministicInsightEngine`.
- Five milestone types, chosen for a clean "this is genuinely a new best"
  comparison from data already computed elsewhere in this app: the app's
  own centerpiece readiness score reaching a new recorded high
  (`ReadinessScoreHistoryRepository#findScoresByAccountId`), a habit
  streak reaching its own longest-ever length (`JournalService#getStreaks`,
  minimum 5 days so a 1-day tie isn't treated as meaningful), the most
  recent tracked week's average steps beating every other tracked week
  (`ActivityInsightService#computeStepTrends`), a calendar month's VO2max
  estimate beating every other tracked month (`Vo2MaxService#computeTrend`),
  and a training period with more total strength-training minutes than any
  other tracked period (`StrengthTrainingService#computeTrend`, 6-month
  monthly buckets).
- Deliberately excludes sleep consistency: `SleepInsightService`'s
  consistency score is a single fixed rolling window of recent nights, not a
  series of independent past periods — it doesn't have a clean, honest "new
  personal best" framing the way a cumulative streak/week/month/period does.
- Stateless, computed fresh on every request (like `HealthspanService`/
  `TrainingLoadService`) — no new table to remember past milestones. Every
  comparison is answerable from the other modules' own existing query
  methods; a "shown once" table is a reasonable future enhancement, not a
  first-version requirement.
- New `GET /api/v1/milestones` endpoint and a Flutter dashboard card
  (renders nothing when the list is empty, same convention as
  `ConfidenceBanner`/`LlmBadge`).
- Required exposing `journal.application`/`journal.domain` and
  `activities.application`/`activities.domain` as Spring Modulith
  `@NamedInterface`s (mirroring the pattern already used by `vo2max`/
  `strength`/`sleep`) so the new module could compose their real,
  already-computed outputs instead of re-querying the same tables.
- Extended with a sixth check reusing the app's own real recorded-day
  history: the most recently recorded day's readiness score reaching a new
  high across every other real recorded day
  (`ReadinessScoreHistoryRepository#findScoresByAccountId`, minimum 2
  recorded days so there's a genuine prior day to compare against). Verified
  against `Map` iteration order — the "most recent" comparison uses the max
  real date, not insertion/iteration order.

### Added — Local LLM rephrasing wired into all three coach touchpoints (parity-matrix row 7)
- `LlmInsightService` talks to Ollama's REST API directly via a plain Spring
  `RestClient`, not Spring AI's `ChatClient` — live testing found Spring AI
  1.0.1's `OllamaOptions` has no field to express Ollama's real
  `"think": false` request parameter (confirmed via `javap` inspection of the
  full field list), and the app's default model (`qwen3:8b`) is unusably
  slow (18-45+s, frequent timeouts) with its default internal "thinking"
  left on. The direct REST call with `think: false` set explicitly brought
  response times to a consistent ~4-7s. The now-fully-unused
  `spring-ai-ollama-spring-boot-starter` dependency was removed.
- All three coach endpoints (`/coach/insight`, `/coach/why`,
  `/coach/habit-cue`) share this one service: only the single piece of free
  text each returns (summary/answer/reasoning) is optionally rephrased in a
  warmer voice — every other field (score, factors, cited metrics,
  confidence) always comes from the deterministic engine, never the LLM,
  per this app's "LLM explains, never computes" core principle.
- New `llmUsed` field on `Insight`/`WhyAnswer`/`HabitCue`, mutually exclusive
  with the existing `fallbackUsed` — closes a real ambiguity where
  `fallbackUsed=false` alone couldn't distinguish "LLM never attempted" from
  "LLM attempted and succeeded." Flutter surfaces this honestly as a small
  "Personalized locally" badge (`widgets/llm_badge.dart`), shown only when a
  specific piece of text was genuinely LLM-rephrased.
- Fixed a real, previously-undiscovered version-pinning bug found via live
  testing: `infrastructure/docker/docker-compose.yml`'s pinned
  `ollama/ollama:0.5.1` image predates Qwen3 support entirely and can't pull
  it at all. Bumped to `0.32.15`.
- Live-verified against a real local Ollama server: `/coach/insight` and
  `/coach/why` confirmed returning `llmUsed: true` with genuine
  fact-grounded rephrased text.

### Added — Unified Trends overview page (parity-matrix row 8)
- New `apps/flutter/lib/features/trends/trends_page.dart`, reachable from
  the overflow "More" menu (`/trends` route) on every tab rather than a 5th
  bottom-nav tab — restructuring the four primary tabs (Today/Sleep/
  Activity/Journal) is a bigger IA decision deliberately left for a
  dedicated pass, not bundled into this one.
- One screen gives an honest at-a-glance summary of all four trend types
  this app tracks (sleep, steps, VO2max, strength training) by reusing the
  exact same real endpoints their existing detail pages already call — no
  new backend logic, no duplicated charts/history/methodology. Each section
  shows the real most-recent value plus a trend direction (first-half vs.
  second-half of the window, same honest convention `_TrendSummaryRow`
  already used) and taps through to the existing full detail page.
- Each of the four sources is fetched independently and best-effort — one
  missing/failed source never blanks the rest of the overview; honest empty
  state per section, never a fabricated value.

### Verified — Live end-to-end verification of previously untested rows
- Six rows (18 VO2max, 20 Sleep Planner strain adjustment, 24 Healthspan,
  25 Strength trends, 26 Workout generator, 30 Monthly report) had shipped
  unit-tested but explicitly flagged "not yet live-verified against a
  running backend." Booted a throwaway backend instance against the real
  dev database and made read-only requests against each: every one behaved
  honestly against this account's real (mostly sparse) data — correct
  `confidence: "none"`/empty-array/`sufficientHistory: false` responses with
  the specific missing-data reasons named, never an error or a fabricated
  number — and the workout generator produced a real, fully-formed
  deterministic plan end-to-end.
- A follow-up pass live-verified four more rows (1 Body Battery factor, 3
  Sleep, 9 long-term trend rollups, 22 biomarker import) and corrected a
  stale claim in row 1 ("should activate on the next call") that turned
  out, on checking, to be untestable right now for an honest reason: this
  dev account's synced measurement data stops at 2026-08-03, over a month
  before any 2026-09 check — documented in the matrix's own Notes section
  rather than left implicit.

### Fixed — Flutter test suite couldn't run at all; added first widget tests
- `web_download.dart` unconditionally imported `dart:html`, which doesn't
  exist on the native VM `flutter test` runs on. The one pre-existing
  Flutter test (a bare app-shell smoke test) failed to even compile the
  moment it transitively imported `app.dart` → `privacy_page.dart` →
  `web_download.dart` — meaning it had likely never successfully run via
  `flutter test` in this project's history, only `flutter analyze`/
  `flutter build web` (both target web, where `dart:html` exists).
- Split into the standard Dart conditional-import facade:
  `web_download_web.dart` (the real implementation, web only) and
  `web_download_stub.dart` (throws `UnsupportedError` rather than silently
  no-oping) via `export ... if (dart.library.html)`. No behavior change on
  the web target this app actually ships to.
- Added this project's first real widget tests: `LlmBadge` (the one
  visible signal a piece of coach text was genuinely LLM-rephrased, row 7)
  and `ConfidenceBanner` (the shared surface behind this app's "every
  score exposes its own uncertainty" principle, row 12) — both
  self-contained, no network/backend dependency, previously untested
  beyond compiling. `flutter test` now runs and passes (7 tests).
- Follow-up: added widget tests for `LoadingView`/`ErrorView`/`EmptyView`,
  the shared placeholders nearly every data page renders through (13 tests
  total).

### Chore — Removed dead dependencies (backend + Flutter)
- Backend: `testcontainers-junit-jupiter`/`testcontainers-postgresql` were
  declared test dependencies with zero actual usage anywhere (no
  `@Testcontainers`, no `PostgreSQLContainer`, no `jdbc:tc:` URL). This
  project's tests don't boot a real database at all today. Removed;
  33/33 backend suites still pass.
- Flutter: `flutter_riverpod`, `riverpod_annotation`, `riverpod_generator`,
  `freezed_annotation`, `freezed`, `json_annotation`, `json_serializable`,
  and `build_runner` had zero actual usage — no `@freezed`/
  `@JsonSerializable`/`@riverpod` annotation, no generated `.g.dart`/
  `.freezed.dart` file, not even a plain `Provider`/`StateProvider`
  declaration. Riverpod was wired in only as inert scaffolding
  (`ProviderScope` in `main.dart`, `ConsumerStatefulWidget` on
  `DashboardPage`) that nothing ever read from — every page actually
  manages its own local `State` with `setState` and parses JSON by hand.
  Removed the scaffolding along with the dependencies (`DashboardPage` is
  now a plain `StatefulWidget`); `flutter pub get` dropped 47 transitive
  dependencies. `fl_chart` is kept — genuinely unused today too, but a
  plausible future chart-widget candidate rather than abandoned
  scaffolding, so left alone with a comment explaining why.

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