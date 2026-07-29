# Phase Gate Report — Phase 3: Insight Intelligence (EC1/EC2 progress)

- **Phase**: 3 — Insight intelligence
- **Date**: 2026-07-29
- **Status**: PASS — all 7 exit criteria addressed (EC6 has one noted coverage gap — see below).
- **Version**: 0.3.0-SNAPSHOT

## Context

EC1 and EC2 were implemented and self-reported as "verified end-to-end."
Quality gate review found the self-verification was compilation- and
unit-test-level only — it never caught three real bugs because every DB
query in the new services silently swallowed exceptions with no logging,
turning real failures into silently-wrong-or-missing data instead of
visible errors. All three were reproduced independently with real data
(not just re-reading the original test output) and fixed.

## Bugs found and fixed

### Bug 1: Training load silently broken for any realistic dataset
- **Root cause**: `CurrentMetricsService.fetchStepsSum()` had leftover dead
  code — a first query using `GROUP BY DATE(time)` through
  `jdbcTemplate.queryForObject()`, which requires exactly one row.
  Reproduced directly in SQL: with 3 days of step data (the same data used
  in the original "verified" test), that query returns 3 rows, not 1 —
  throws `IncorrectResultSizeDataAccessException` every time, silently
  caught, returns 0.0. Training load (ACWR) was reported as "missing" any
  time there was more than one day of step data — the normal case, not an
  edge case.
- **Fix**: deleted the dead first query; only the correct averaging query
  remains.
- **Verified**: `/latest` against the same 3-day dataset now returns
  `"Training load (ACWR)"` as a real factor (`value: 1.0, direction:
  NEUTRAL`) instead of excluding it as missing.

### Bug 2: Sleep-duration approximation fed nonsense values into the score
- **Root cause**: `readingCount * 0.25 hours` assumes exactly 4
  sleep_stage readings per hour. Against the actual test data this
  computed 1.0 hours, which then became the single largest negative
  factor in the score (`Sleep duration vs. need: -10.0 contribution`).
- **Fix**: added a plausibility bound (2–14 hours) in both
  `CurrentMetricsService.fetchSleepDuration()` and
  `BaselineService.computeSleepDurationBaseline()`. Values outside that
  range are logged and reported as missing data rather than fed into the
  score — matches how the codebase already treats genuinely absent data
  (see Training load), rather than silently corrupting the score's
  biggest factor with noise.
- **Verified**: `/latest` now shows `"Excluded from weighted sum: Sleep
  duration vs. need."` against the same dataset instead of a fabricated
  -10.0 contribution.

### Bug 3: "vs Yesterday" never actually compared against yesterday
- **Root cause**: `ReadinessController.diff()` fabricated "prior" by
  plugging the user's own rolling baseline averages back in as if they
  were yesterday's raw metrics. Since deviation = current − baseline, and
  prior's "current" *was* the baseline, every prior-day deviation was
  mathematically forced to exactly 0 by construction — confirmed by the
  original response showing `"priorValue": 0.0` on every single factor.
  The Flutter UI labeled this "vs Yesterday," which was inaccurate — it
  was actually "today vs. your own average."
- **Fix**: added real persistence (`readiness_score_history` table,
  Flyway V04; `ReadinessScoreHistoryRepository`). `/latest` and `/diff`
  now upsert today's computed score. `/diff` looks up the actual
  persisted score for yesterday's date; if found, computes a genuine
  diff labeled `"comparedAgainst": "yesterday"`. If no prior day has been
  recorded yet (e.g. first day of use), returns an honest
  `"comparedAgainst": "no_prior_data"` response with a clear message
  instead of fabricating a comparison. `ScoreDiff` gained a
  `comparedAgainst` field; the Flutter "vs Yesterday" card now reads it
  and only shows that label (and the delta/trend icon) when there's
  real prior-day data — otherwise shows "Day-over-day" with just the
  honest summary text.
- **Verified end-to-end**: called `/diff` with no history — got
  `comparedAgainst: "no_prior_data"`, `factorDiffs: []`, honest message.
  Inserted a real, distinct "yesterday" score directly into the new
  table (different score, different factor set — only HRV/RHR present,
  matching a realistic sparse day). Called `/diff` again — correctly
  picked up the real prior score, computed genuine per-factor deltas
  against it (including correctly identifying factors present today but
  absent in the sparse prior record as new), and returned
  `comparedAgainst: "yesterday"`.

### Contributing factor: silent exception swallowing
Every query method in `BaselineService` and `CurrentMetricsService` had
`catch (Exception e) { // silently skip }` with no logging — precisely
why Bug 1 went undetected through the original "verified end-to-end"
pass. Replaced every one with `log.warn(...)` including the exception.

## Exit criteria status

### EC1: Personalized rolling baselines — PASS
- Verified: `/baseline` returns real per-metric averages with honest
  sample sizes and confidence from actual measurement history (24
  synthetic rows spanning HRV/RHR/stress/steps/sleep_stage).
- `PersonalBaselineTest` (5 tests).

### EC2: Score-diff and reasoning on tap — PASS (after fixes above)
- Verified end-to-end against both a real prior-day score and the
  no-prior-data first-use case, per Bug 3 above.
- `ScoreDiffServiceTest` (4 tests, updated for the new `comparedAgainst`
  parameter).

### EC3: Local coach answering "why" questions with cited metrics — PASS
- Found and fixed an additional gap while implementing this: `CoachController
  .getInsight()` was still using Phase 1's hardcoded synthetic
  `ReadinessInputs` (never upgraded when EC1 added real personalized
  baselines) — answering "why" questions grounded in fake data would have
  defeated the point. Now uses `BaselineService` + `CurrentMetricsService`
  like the readiness endpoints.
- New `DeterministicInsightEngine.explainReadiness(ReadinessScore,
  Optional<ScoreDiff>)` — cites the actual ranked factor contributions
  (not generic advice), notes when the score is provisional, and when a
  real prior day exists, explains what changed and why; otherwise says so
  explicitly rather than fabricating a comparison.
- New `GET /api/v1/coach/why` endpoint. Works fully via the deterministic
  fallback — no LLM required, per core principle #1.
- **Flutter**: "Why?" button on the coach card, expands to show the
  answer and a ranked list of cited metrics with their contributions.
- **Tests**: 4 new tests on `DeterministicInsightEngineTest` — cited
  metrics correspond to real factors, no-prior-data case says so
  explicitly, with-prior-data case cites the actual change, metrics
  sorted by absolute impact.
- **Verified end-to-end**: called `/coach/insight` — confirmed it now
  reflects the same real score as `/latest` (57/100, Training load
  present) instead of the old hardcoded synthetic score. Called
  `/coach/why` — confirmed it cited real ranked factors and correctly
  referenced the actual persisted "yesterday" score from the EC2
  verification (improved by 5 points, mainly thanks to RHR deviation).

### EC4: Confidence & limitation displays on all surfaces — PASS
- Audit found `SleepController`, `ActivitiesController`, and
  `DataQualityController` had **zero** confidence/limitation disclosure —
  100% hardcoded placeholder data presented with no warning at all. A
  user could easily read "85 sleep score" or a "data quality" dashboard
  as real. Added explicit `confidence`/`limitations` fields to all three
  (`confidence: "none"`, limitations explaining this is placeholder data
  pending the normalization pipeline, per Phase 2's own disclosed
  limitation).
- `ScoreDiff` had no confidence field at all — added one (inherits
  today's score confidence, since a diff carries the same uncertainty as
  the score it's built from).
- **Flutter**: new shared `ConfidenceBanner` widget (`lib/widgets/`) —
  renders nothing when there's nothing to disclose, otherwise shows the
  confidence level and every limitation. Wired into the Sleep, Activities,
  and Data Quality pages, so the disclosure actually reaches the user
  instead of sitting unused in an API response field.
- **Verified end-to-end**: called all four endpoints — confirmed
  `sleep/summary` and `activities/summary` return `confidence: "none"`
  with clear placeholder-data limitations; `data-quality/summary`
  explicitly discloses that its own quality figures are placeholder;
  `readiness/diff` now carries a real `confidence` value.

### EC5: Real sleep/training-load insights — PASS
- **Sleep**: new `sleep/domain` + `sleep/application` layer
  (`SleepInsightService`) computes real per-night summaries from
  `sleep_stage` measurements — stage encoding matches
  `packages/test-data/synthetic_generator.py`
  (`SLEEP_STAGES = ["deep","rem","light","awake"]`, value = index).
  Sleep score is an original v0.1 formula (duration vs. 7.5h target +
  proportion of deep/REM sleep) — documented, not a vendor reproduction.
  Confidence is honestly based on reading density (a full night is ~32
  readings at 15-min intervals) rather than always claiming high
  confidence. Unlike the readiness score (which excludes unreliable
  sleep data from the blended factor), this dedicated view shows the
  computed value with a clear low-confidence caveat instead of hiding
  it — more useful on a page whose whole purpose is showing sleep data.
- **Activities**: new `activities/domain` + `activities/application`
  layer (`ActivityInsightService`) computes real step counts from
  measurement data. Calories/active minutes/active zone minutes are
  honestly reported as `null` (not fabricated as estimates from steps)
  — the data model has no measurement type for them yet. Both surfaces
  use "most recent day with data" rather than strict calendar "today",
  matching real-world usage once import lags a day behind.
- **Flutter**: updated models for nullable calorie/active-minute fields;
  `_ActivitySummaryCard` shows "—" / "not tracked" instead of literal
  "null" text.
- **Verified end-to-end**: hand-checked the sleep score formula against
  real data (durationRatio=0.133, restorativeRatio=0.5 → score 31.67 →
  rounds to 32 — matched the API response exactly). Cross-checked
  activity summary/trends against direct SQL queries on the same data —
  steps matched exactly (8000/9000/8500 across 3 real days). Confirmed
  trends honestly return only the 3 real days that exist rather than
  fabricating a full 7-day range.
- No dedicated unit tests added for `SleepInsightService`/
  `ActivityInsightService` — both are thin DB-query wrappers where a
  mocked JdbcTemplate test would have low value; live verification
  against the real database (above) is stronger evidence, consistent
  with how `ReadinessScoreHistoryRepository` was handled in EC2.

### EC6: Journal & behaviors taxonomy — PASS (with one noted coverage gap)
- The `journal_entries` table already existed from the Phase 0 schema but
  no module was ever built on it. Added `journal/domain`,
  `journal/application` (`JournalService`), `journal/adapter/in`
  (`JournalController`) following the same layering as the rest of the
  codebase.
- Taxonomy: 6 categories (Sleep, Nutrition, Recovery, Mental wellbeing,
  Training, Supplements), 33 behaviors total — deliberately broader than
  the original Phase 1 stub (6 behaviors, no categories) but not
  attempting the ~140-behavior breadth some competitor products offer,
  per the product backlog's explicit scoping note on this exit
  criterion. The taxonomy is a suggested list, not an enforced
  constraint — free-text custom behaviors are still accepted, matching
  the existing schema (no DB-level enum).
- Category is round-tripped through the existing free-text `behavior`
  column as `"category::behavior"` rather than requiring a schema
  migration — decoded back into separate `category`/`behavior` fields on
  read, with a fallback to `"Other"` for any pre-existing data without
  the encoding.
- Entries are always stored with `treated_as_untrusted = true`, per the
  schema's own intent and `docs/product/parity-matrix.md` row 5/6.
- New `GET /api/v1/journal/behaviors`, `POST /api/v1/journal/entries`,
  `GET /api/v1/journal/entries` endpoints. Bean Validation rejects blank
  behaviors (verified: returns 400).
- **Flutter**: new `journal_page.dart` — category dropdown + behavior
  chips + optional value/note fields in a bottom sheet, entry list with
  empty/loading/error states, wired into dashboard navigation.
- **Tests**: 6 new `JournalServiceTest` cases — taxonomy breadth
  (asserts total behavior count exceeds 3x the original stub),
  category/behavior encoding and decoding round-trip, legacy-data
  fallback, untrusted-input flag always set.
- **Verified end-to-end**: booted the real backend, called
  `POST /journal/entries` twice with real data, confirmed correct
  auto-generated IDs, confirmed the raw DB row shows the expected
  `"Nutrition::Alcohol"` encoding, confirmed `GET /journal/entries`
  correctly decodes it back and orders most-recent-first, confirmed
  blank-behavior submission is rejected with 400.
- **Coverage gap, noted rather than hidden**: unlike EC1-EC5, the new
  Flutter journal page itself was only verified via `flutter analyze`
  and `flutter build web` — not an actual interactive render/click-
  through in a live browser. Static analysis didn't catch the Phase 1
  `textBaseline` runtime crash earlier in this project, so this is a
  real, not merely theoretical, gap. Recommend a manual click-through of
  the journal flow (add an entry via the bottom sheet, confirm it
  appears in the list) before considering EC6 fully closed.

### EC7: Exploratory correlations — PASS
- New `journal/domain/BehaviorCorrelation.java`,
  `journal/application/CorrelationService.java`. Deliberately a simple
  group-mean comparison (readiness on days a behavior was logged vs.
  not), not dressed up with statistics like p-values that a handful of
  data points can't actually support — consistent with the project's
  "explainable over sophisticated" principle.
- Reused `ReadinessScoreHistoryRepository` (from EC2, already exposed
  via the `readiness.application` NamedInterface) rather than querying
  `readiness_score_history` a second time via raw SQL from the journal
  module — added a `findScoresByAccountId()` method to it instead of
  duplicating the query. Confirmed Modulith verification allows the new
  cross-module dependency (it's a legitimate, already-exposed interface,
  not a new violation).
- **Minimum sample size**: requires ≥3 days in BOTH the logged and
  not-logged groups — behaviors below this are excluded entirely, not
  shown with fabricated confidence.
- **Confidence**: only ever "low" or "medium" — "high" is never used for
  a comparison this small, by design (enforced structurally, not just by
  convention).
- **No causation claims**: every result's limitations always include
  explicit correlation-not-causation language and a small-sample caveat.
- **Tests**: 6 new `CorrelationServiceTest` cases, including
  `confidence_isNeverHigh_evenWithLargeSamples` (encodes the design
  principle as an executable assertion, same pattern as the existing
  `noMedicalDiagnosisLanguage` test) and a hand-verified group-mean math
  test.
- **Flutter**: new `_CorrelationsCard` on the journal page, showing each
  correlation with its own correlation-not-causation caption, sample
  sizes, and confidence — not hidden in an unused API field.
- **Verified end-to-end with real data**: seeded 6 days of
  `readiness_score_history` (3 lower-scoring, 3 higher-scoring) and 3
  matching journal entries, then called the live endpoint. Result
  included a real entry from *earlier in this same session's* EC6
  testing (today's real persisted score) alongside the seeded data —
  hand-traced the exact math: logged-day average 48.0
  ((45+48+42+57)/4), not-logged average 69.25 ((75+78+72+52)/4),
  matching the API response exactly. A behavior logged only once during
  EC6 testing ("Cold exposure") was correctly excluded from the results
  for being below the sample-size threshold — confirmed by its absence.

## Phase 3 status: all 7 exit criteria addressed

EC1-EC5 and EC7 fully pass with real-data end-to-end verification. EC6
passes with one explicitly noted coverage gap (the Flutter journal page
itself was checked via `flutter analyze`/`build web` but not an
interactive click-through in a live browser — worth a manual pass).

Every exit criterion in this phase surfaced at least one real bug or
gap during independent verification that a build-passing check alone
would have missed — this session's own EC6 commit even turned out to be
missing its own test file, caught by applying the same discipline to
this session's own work as to Cline's. That pattern held for all seven:
the value was consistently in the verification step, not just the
implementation.

## Final verification
- `./gradlew build` — BUILD SUCCESSFUL, 42 tests pass.
- `flutter analyze` — No issues found.
- `flutter test` — All tests passed.
- `flutter build web` — ✓ Built build/web.
- All fixes verified against the real running backend + Postgres, not
  just unit tests — including reproducing Bug 1 directly in SQL before
  fixing it, to confirm the diagnosis rather than guess.

## No real health data
- All data is synthetic (the same 24-measurement fixture used throughout
  Phase 3 testing, plus one manually-inserted synthetic "yesterday" score
  row for diff verification).
- No real health data in any fixture, test, or prompt.
