# Test Data — Synthetic Generator

> **No real health data.** This package generates **synthetic** wearable data
> and synthetic FIT fixtures only. All files are named `synthetic*.fit`.

## Purpose

- Generate synthetic HR, HRV, RHR, sleep, steps, stress, respiration, SpO2,
  and activity data for development and automated testing.
- Provide at least one `synthetic*.fit` fixture for FIT import testing.
- Ensure no real health data is ever used in fixtures, logs, or prompts.

## Usage (Phase 1 target)

```bash
# Generate synthetic data and load it into the local database
./scripts/load-synthetic.sh
```

## Contents

- `synthetic_generator.py` — synthetic data generator (Python, deterministic).
- `synthetic-activity.fit` — synthetic FIT fixture (generated, not from a real device).
- `synthetic-*.json` — synthetic JSON exports for testing.
- `synthetic-biomarkers.csv` — synthetic blood biomarker (lab bloodwork) CSV
  fixture for `biomarkers.application.BiomarkerCsvParser` tests. Fabricated
  values only, not from any real lab result.

## Rules

- All FIT files must be named `synthetic*.fit`.
- The CI health-data guard rejects any `*.fit` outside this package
  (except `apps/garmin-connect-iq/**`).
- No real health data, ever.