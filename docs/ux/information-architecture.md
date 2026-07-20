# Information Architecture

> Dashboard hierarchy and navigation for Open Wearable Insights.
> Original visual language — does not imitate any vendor's design.

## Top-level navigation

```
Open Wearable Insights
├── Dashboard (home)
├── Readiness
├── Sleep
├── Training Load
├── Activities
├── Insights & Coach
├── Journal
├── Correlations
├── Import
├── Privacy
└── Settings
```

## Dashboard (home)

- **Readiness score** (prominent, with calibration state).
- **Top factor contributions** (positive & negative).
- **Sleep summary** (last night).
- **Training load** (ACWR snapshot).
- **Daily coach** card (deterministic insight; optional LLM).
- **Data-quality banner** (if stale/incomplete).

## Readiness

- Score + confidence + algorithm version.
- Factor breakdown (HRV deviation, RHR deviation, sleep, training load, stress,
  data completeness).
- Baseline period + personal baseline values.
- Missing-data treatment.
- Plain-language explanation.
- Known limitations.
- Trend (7/30/90 days).

## Sleep

- Last night summary (duration, time-in-bed, stages, efficiency).
- Sleep consistency (schedule regularity).
- Sleep need vs. actual (deficit/surplus).
- Stage breakdown chart.
- Wake-time/bedtime trend.
- Data-quality warnings.

## Training Load

- Acute vs. chronic load (ACWR).
- Load by activity type.
- Load trend.
- Provenance of load values (HR zones, TRIMP, vendor metrics — never mixed
  without normalization).

## Activities

- List of activities (type, date, duration, distance, pace).
- Activity detail (HR zones, elevation, cadence, power if available).
- Source/provenance per activity.

## Insights & Coach

- Deterministic insight feed.
- Coach Q&A ("Why is my readiness lower today?").
- Each answer cites contributing metrics + provenance.
- Recommended training intensity (hard/easy/rest) with factors & cautions.
- LLM status indicator (available / fallback).

## Journal

- Entry list (behaviors, RPE, soreness, fatigue, notes).
- Add entry form.
- Treated as untrusted input (prompt-injection-safe).

## Correlations

- Behavior ↔ outcome pairs.
- Sample count, effect direction, uncertainty.
- "Correlation is not causation" caution.
- Minimum sample-size threshold; no conclusion if insufficient.

## Import

- Source directory picker (local app-data dir outside repo).
- Dry-run preview.
- Progress & error reporting.
- Undo.

## Privacy

- Export (complete local data export).
- Delete (complete local data deletion with confirmation).
- Consent management.
- Data flow transparency (what stays local, what is optional).

## Settings

- Local model configuration (Ollama URL, model profile).
- Data retention preferences.
- Units preferences.
- Accessibility settings.
- About / version / algorithm versions.

## Responsive breakpoints

- Mobile (≤ 600px): single-column, bottom nav, cards stack.
- Tablet (601–1024px): two-column, rail nav.
- Web (> 1024px): multi-column dashboard, side nav.

## States to design for (every data-driven screen)

- Empty (no data yet)
- Loading
- Calibration (provisional state)
- Stale data (data-quality warning)
- Partial data
- Error
- Populated (normal)