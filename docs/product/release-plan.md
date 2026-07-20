# Release Plan

> Phased delivery. Each phase has a clear milestone and exit criteria.
> Local-first operation is preserved across all phases.

## Phase 0 — Discovery & foundation (current)

**Milestone**: Repository, docs, ADRs, CI skeleton, local infra, scaffolds.

**Exit criteria**:
- All required docs exist with real content.
- ADRs for major decisions recorded.
- `docker-compose.yml` starts Postgres+TimescaleDB on loopback.
- `services/api` builds (`./gradlew build`).
- `apps/flutter` analyzes clean (`flutter analyze`).
- `packages/test-data` has a synthetic generator + synthetic FIT fixture.
- CI skeleton runs health-data guard, gitleaks, build, SBOM, Trivy.

## Phase 1 — Local vertical slice

**Milestone**: Synthetic data → FIT import → readiness → insights → coach.

**Exit criteria** (13 acceptance criteria from the task):
1. Starts locally via documented commands.
2. Starts PostgreSQL/TimescaleDB + Spring Boot API via Docker Compose.
3. Starts Flutter web app.
4. Loads synthetic wearable data.
5. Imports ≥1 synthetic FIT fixture.
6. Normalizes the data.
7. Computes a versioned provisional readiness score.
8. Displays result + contributing factors.
9. Produces a deterministic textual insight.
10. Optionally produces a local Ollama explanation.
11. Works when Ollama is unavailable.
12. Passes automated tests.
13. Contains no real health data.

## Phase 2 — Personal Garmin import

**Milestone**: Import my Garmin export locally (no real data in repo).

**Exit criteria**:
- Local import folder outside repo; guided UI.
- Dry-run validation; duplicate detection; unsupported-record reporting.
- Progress reporting; explicit errors; undo.
- Sleep, activity, trend views.
- Data-quality dashboard.

## Phase 3 — Insight intelligence

**Milestone**: Personalized, explainable insights and a grounded local coach.

**Exit criteria**:
- Personalized rolling baselines.
- Readiness factors with positive/negative contributions.
- Sleep, training-load insights.
- Journal & behaviors.
- Exploratory correlations (sample count, uncertainty, no causation claims).
- Local coach answering "why" questions with cited metrics.
- Confidence & limitation displays.
- Insight evaluation suite.

## Phase 4 — Mobile applications

**Milestone**: iOS & Android builds with native health adapters.

**Exit criteria**:
- iOS & Android builds.
- HealthKit adapter.
- Health Connect adapter.
- Local notifications & secure config.
- Mobile-specific permission flows.

## Phase 5 — Garmin Connect IQ

**Milestone**: Watch app/widget.

**Exit criteria**:
- Simulator-supported target.
- Device capability matrix.
- Shows readiness, sleep, factors, recommended intensity, sync time,
  data-quality warning.
- Battery-conscious behavior.

## Phase 6 — Productization

**Milestone**: Optional hosted architecture, preserving local-first.

**Exit criteria**:
- Official Garmin API (if approved).
- Hosted multi-user option.
- OIDC, tenant isolation, cloud secrets.
- Hosted DB & object storage.
- App Store / Play Store / Connect IQ Store distribution (if pursued).

## Versioning

- Pre-1.0: `0.<phase>.<increment>` (e.g., `0.1.0` = Phase 1 first slice).
- Post-1.0: Semantic Versioning.
- Each release updates `CHANGELOG.md` and `parity-matrix.md`.