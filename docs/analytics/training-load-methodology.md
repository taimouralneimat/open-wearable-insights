# Training-Load Methodology

> Transparent internal training-load model. Does not reproduce any vendor's
> proprietary formula. Never combines incomparable values without
> normalization and provenance.

## Methods investigated

- **Session RPE**: duration × perceived exertion (1–10). Simple; requires
  user input.
- **Heart-rate-zone load**: time in each HR zone × zone weight. Objective;
  requires HR data.
- **TRIMP variants** (Banister, Lucia): duration × intensity weighting.
  Objective; requires HR or HR-reserve.
- **Acute vs. chronic workload ratio (ACWR)**: acute load (e.g., 7-day) /
  chronic load (e.g., 28-day). Risk indicator.
- **Vendor training-load metrics**: stored as-is with provenance; never mixed
  with app-computed metrics without normalization.

## Decision (Phase 3)

- Compute **HR-zone load** as the primary app-derived metric (objective,
  requires HR data which Garmin provides).
- Compute **ACWR** from the app-derived load.
- Accept **Session RPE** as a secondary, user-input-based metric.
- Store vendor metrics separately with provenance; do not average across
  incompatible methods without explicit normalization.

## Provenance

- Every load value records: method, source, algorithm version, inputs.
- Normalization across methods is explicit and documented.

## Limitations

- ACWR is a risk indicator, not a predictor.
- HR-zone load depends on accurate zone boundaries (user-configurable).
- Not a medical measure.