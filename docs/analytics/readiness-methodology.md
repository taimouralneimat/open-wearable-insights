# Readiness Methodology

> Transparent, versioned readiness algorithm. Original — does not reproduce
> any vendor's proprietary formula.

## Algorithm version

- **v0.1** (Phase 1): provisional score; limited factors; calibration state.
- Future versions increment sequentially; golden tests pin outputs per version.

## Inputs (Phase 1)

| Factor | Source | Unit | Notes |
|---|---|---|---|
| HRV deviation | HRV measurements (RMSSD) | ms | deviation from personal rolling baseline |
| Resting HR deviation | RHR measurements | bpm | deviation from personal rolling baseline |
| Sleep duration | Sleep sessions | hours | vs. sleep need estimate |
| Recent training load | Training-load module | load units | acute load |
| Stress | Stress measurements | score | average over recent window |
| Data completeness | All | % | fraction of expected metrics present |

## Baseline

- Personal rolling baseline (e.g., 30-day, expanding minimum).
- **Provisional state** when baseline period is insufficient (e.g., < 7 days).
- Score is clearly marked "provisional" during calibration.

## Computation (v0.1, deterministic)

1. Compute each factor's deviation from baseline (z-score or % deviation).
2. Apply fixed, documented weights (sum to 1.0).
3. Map weighted sum to a 0–100 scale.
4. Compute confidence/data-quality level from completeness & freshness.
5. Record positive and negative factor contributions.
6. Record missing-data treatment (exclusion vs. imputation; explicitly stated).

## Output (explainability contract)

Every readiness score exposes:
- Input variables (which measurements fed it).
- Data source and provenance (source, batch, algorithm version).
- Baseline period (e.g., 30-day rolling).
- Contribution of each factor (positive/negative, with value).
- Missing-data treatment.
- Confidence or data-quality level.
- Algorithm version.
- Plain-language explanation.
- Known limitations.

## Golden tests

- Known-good synthetic inputs → expected scores.
- Failures require an ADR-level methodology change, not a silent rebaseline.

## Limitations

- v0.1 is provisional; weights are initial estimates, not empirically tuned.
- Not a medical measure; not a diagnostic.
- Does not reproduce any vendor's proprietary formula.