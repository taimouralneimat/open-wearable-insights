# Phase Gate Report — Phase 3: Insight Intelligence (EC1/EC2 progress)

- **Phase**: 3 — Insight intelligence
- **Date**: 2026-07-29
- **Status**: EC1/EC2 PASS (after bug fixes and end-to-end verification). EC3-EC7 not started.
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

### EC3-EC7 — not started
Per docs/product/release-plan.md: local coach "why" answering,
confidence/limitation displays on all surfaces, real sleep/training-load
insights, expanded journal/behaviors, exploratory correlations.

## Final verification
- `./gradlew build` — BUILD SUCCESSFUL, 38 tests pass.
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
