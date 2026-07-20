# Backlog

> Executable backlog with acceptance criteria. Stories are grouped by phase.
> Each story has a unique ID for traceability in PRs.

## Phase 0 — Discovery & foundation

### P0-1: Repository bootstrap
- [ ] `.gitignore` excludes health data, secrets, local state.
- [ ] `LICENSE` (Apache 2.0), `NOTICE`, `README.md`, `SECURITY.md`,
      `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `CHANGELOG.md` exist.
- [ ] `.clinerules/` rules present.
- [ ] `.github/` templates, Dependabot, CI skeleton present.
- [ ] `docs/` structure created with real content.

### P0-2: Local infrastructure
- [ ] `infrastructure/docker/docker-compose.yml` starts PostgreSQL 16 +
      TimescaleDB on loopback.
- [ ] Ollama service (loopback only) is optional.
- [ ] Volumes are gitignored.

### P0-3: Backend scaffold
- [ ] `services/api` Gradle Kotlin DSL project with wrapper.
- [ ] Spring Boot 3.5.6, Spring Modulith, Spring AI 1.0.1, Spring Security,
      OpenAPI 3, Flyway, Bean Validation, Testcontainers, Micrometer.
- [ ] Versions pinned in `gradle/libs.versions.toml`.
- [ ] Module package structure for all 15 target modules.
- [ ] `application.yml` binds to loopback.

### P0-4: Flutter scaffold
- [ ] `apps/flutter` project with Riverpod, go_router, Dio, Freezed,
      fl_chart, flutter_secure_storage.
- [ ] Versions pinned in `pubspec.yaml`.
- [ ] Feature-oriented `lib/` structure.

### P0-5: Shared packages
- [ ] `packages/api-contracts` — OpenAPI/DTOs.
- [ ] `packages/test-data` — synthetic generator + synthetic FIT fixture.

## Phase 1 — Local vertical slice

### S1: Start local stack
- [ ] `./scripts/dev-up.sh` starts Postgres+TimescaleDB (+ Ollama if installed).
- [ ] All services bind to `127.0.0.1`.
- [ ] `./scripts/dev-down.sh` stops and removes containers.

### S2: Load synthetic wearable data
- [ ] `packages/test-data` generates synthetic HR, HRV, RHR, sleep, steps,
      stress, respiration, SpO2, activities.
- [ ] A loader command populates the database with synthetic data.
- [ ] No real health data is used.

### S3: Import synthetic FIT fixture
- [ ] A `synthetic*.fit` fixture exists under `packages/test-data`.
- [ ] The ingestion module parses the FIT file using the Garmin FIT SDK.
- [ ] Idempotency: re-importing the same file (same content hash) is a no-op.
- [ ] Provenance (source, import batch, algorithm version) is recorded.

### S4: Normalize to canonical model
- [ ] Raw measurements are normalized into canonical tables.
- [ ] Units are explicitly represented and normalized.
- [ ] UTC storage with source time zone preserved.
- [ ] JSONB raw payloads retained with provenance.

### S5: Compute provisional readiness score
- [ ] Readiness algorithm v0.1 produces a score from HRV deviation, RHR
      deviation, sleep duration, recent training load, stress, data completeness.
- [ ] Score is marked **provisional** during calibration (insufficient baseline).
- [ ] Confidence/data-quality level is computed.
- [ ] Algorithm version is recorded.
- [ ] Golden test cases pin known-good outputs.

### S6: Display readiness + factor contributions
- [ ] Flutter web dashboard shows readiness score.
- [ ] Factor contributions (positive and negative) are displayed.
- [ ] Drill-down shows inputs, provenance, baseline, missing-data treatment,
      confidence, algorithm version, plain-language explanation, limitations.
- [ ] Calibration state is clearly indicated.

### S7: Produce deterministic textual insight
- [ ] A deterministic template-based insight engine produces a plain-language
      summary grounded in the computed score and factors.
- [ ] No LLM is required for this insight.

### S8: Optional Ollama explanation with fallback
- [ ] If Ollama is available, the coach module requests a structured,
      schema-validated explanation via Spring AI.
- [ ] If Ollama is unavailable or the response is malformed, the deterministic
      fallback is used.
- [ ] The app remains fully usable without the LLM.

### S9: Automated tests & no real data
- [ ] Unit, module-boundary, repository integration (Testcontainers),
      import-parser, idempotency, golden analytics, API contract, LLM
      schema/fallback tests pass.
- [ ] Health-data guard rejects non-synthetic FIT files.
- [ ] No real health data in any fixture, log, or prompt.

## Phase 2 — Personal Garmin import

### S10: Local import folder & guided UI
- [ ] Configurable local app-data directory outside the repo.
- [ ] Import UI selects the directory and lists supported files.

### S11: Dry-run validation
- [ ] Dry-run shows supported files, duplicate detection, unsupported-record
      reporting, and an import summary.
- [ ] No silent drops.

### S12: Import with progress & errors
- [ ] Progress reporting; explicit errors; undo.

### S13: Sleep, activity, trend views
- [ ] Sleep, activity, and trend views populate from imported data.

### S14: Data-quality dashboard
- [ ] Shows completeness, freshness, gaps, and stale-data warnings.

## Phase 3 — Insight intelligence

### S15: Personalized baselines
### S16: Readiness factors with contributions
### S17: Sleep insights
### S18: Training-load insights
### S19: Journal & behaviors
### S20: Exploratory correlations
### S21: Local coach (grounded answers)
### S22: Confidence & limitation displays
### S23: Insight evaluation suite

## Phase 4 — Mobile applications

### S24: iOS & Android builds
### S25: HealthKit adapter
### S26: Health Connect adapter
### S27: Local notifications & secure config

## Phase 5 — Garmin Connect IQ

### S28: Watch app/widget
### S29: Device capability matrix
### S30: Simulator smoke tests

## Phase 6 — Productization

### S31: Official Garmin API (if approved)
### S32: Hosted multi-user option (local-first preserved)
### S33: OIDC, tenant isolation, cloud secrets