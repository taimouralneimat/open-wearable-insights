# User Journeys

## Journey 1 — First run with synthetic data (Phase 1)

1. Taimour clones the repo and runs `./scripts/dev-up.sh`.
2. PostgreSQL + TimescaleDB start on loopback; (optional) Ollama starts on loopback.
3. Taimour runs the backend (`./gradlew bootRun`) and the Flutter web client.
4. He runs `./scripts/load-synthetic.sh` to load synthetic wearable data and a
   synthetic FIT fixture.
5. The dashboard shows a **provisional** readiness score (calibration state),
   factor contributions, and a deterministic plain-language insight.
6. If Ollama is available, an optional LLM explanation appears; if not, the
   deterministic fallback is shown and the app is fully usable.
7. Taimour can drill into the readiness score and see inputs, provenance,
   baseline, missing-data treatment, confidence, algorithm version, and
   limitations.

**Acceptance**: app starts locally; synthetic data loads; readiness score
displays with factors; deterministic insight works; LLM optional; no real data.

## Journey 2 — Import my Garmin export (Phase 2)

1. Taimour places his Garmin Connect export in the local app-data directory
   **outside the repo** (configured path).
2. He opens the Import UI and selects the directory.
3. The system performs a **dry-run validation**: shows supported files,
   duplicate detection, unsupported-record reporting, and an import summary.
4. He confirms the import. Progress is reported; errors are explicit; no silent
   drops.
5. Sleep, activity, and trend views populate from his real data.
6. He can **undo** the import, which removes all records from that batch.

**Acceptance**: local import folder; dry-run preview; duplicate handling;
explicit errors; undo; no real data in the repo.

## Journey 3 — "Why is my readiness lower today?" (Phase 3)

1. Taimour opens the daily coach.
2. He asks: "Why is my readiness lower today?"
3. The coach responds with a grounded answer citing his actual contributing
   metrics (HRV deviation, RHR deviation, sleep duration, recent training load,
   stress, data completeness).
4. Each cited metric links to its source and provenance.
5. The coach offers a recommended training intensity (hard / easy / rest) with
   supporting factors and cautions.
6. If the data is too incomplete, the coach says so explicitly rather than
   guessing.

**Acceptance**: grounded answer; cited metrics; provenance; recommendation
with factors; explicit data-quality limits; no medical advice.

## Journey 4 — Behavioral correlations (Phase 3)

1. Taimour logs journal entries (caffeine, late meals, travel, alcohol,
   perceived exertion, muscle soreness).
2. Over time, he opens the Correlations view.
3. The system shows exploratory correlations between behaviors and outcomes
   (e.g., sleep duration vs. late meals), with sample count, effect direction,
   uncertainty, and a clear "correlation is not causation" caution.
4. If sample size is insufficient, no conclusion is shown.

**Acceptance**: journal input; correlations with sample count and uncertainty;
no causation claims; minimum sample-size thresholds.

## Journey 5 — Data export and deletion (privacy rights)

1. Taimour opens Privacy → Export.
2. The system produces a complete local export of his data.
3. He opens Privacy → Delete.
4. The system deletes all his data (with confirmation) and verifies deletion.

**Acceptance**: complete export; complete deletion; consent flow; no residual
data in the repo or logs.

## Journey 6 — LLM-disabled mode

1. Taimour stops Ollama (or it's unavailable).
2. The app continues to function: readiness scores, factor contributions, and
   deterministic insights all work.
3. LLM-only features are clearly marked as unavailable; no degraded crash.

**Acceptance**: full usability without LLM; deterministic fallback; clear
status indication.