# Sleep Methodology

> Transparent sleep analysis. Original — does not reproduce any vendor's
> proprietary formula.

## Scope (Phase 1–3)

- Sleep duration (total sleep time).
- Time in bed.
- Sleep-stage breakdown (deep, REM, light, awake) when available.
- Sleep efficiency (duration / time-in-bed).
- Sleep consistency (schedule regularity across nights).
- Sleep debt / sleep-need estimate.
- Wake-time and bedtime trend.
- Data-quality warnings (missing stages, short sessions, gaps).
- Comparison against the user's personal baseline.

## Sleep need estimate

- Rolling baseline of actual sleep duration (e.g., 14–30 day average).
- Deficit/surplus accumulated over a recent window.
- Clearly labeled as an estimate, not a clinical prescription.

## Data quality

- Missing stages → flag; do not fabricate.
- Short sessions (< 1h) → likely nap or partial; flag.
- Gaps between sessions → may indicate missing data; flag.
- All warnings surfaced in the UI.

## Provenance

- Sleep sessions carry source (fit/csv/json/synthetic/vendor_api).
- Derived sleep summaries record algorithm version.

## Limitations

- Sleep-need is an estimate, not a medical recommendation.
- Stage accuracy depends on the device's classification, which we do not
  re-derive.