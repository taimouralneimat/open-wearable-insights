# Correlation Methodology

> Exploratory, clearly labeled correlations between journal behaviors and
> outcomes. Correlation is not causation.

## Scope (Phase 3)

- Explore relationships between user-logged behaviors (caffeine, late meals,
  travel, alcohol, RPE, soreness) and outcomes (sleep duration, sleep
  efficiency, next-day readiness, HRV).
- Time-window and lag assumptions are explicit and visible (e.g., "caffeine
  within 6h of bedtime" vs. "next-morning HRV").

## Requirements

- **Minimum sample-size thresholds**: no conclusion if n < threshold
  (e.g., 10–15 paired observations).
- **Show sample count** for every correlation.
- **Show effect direction** (positive/negative) and **uncertainty**
  (confidence interval or dispersion).
- **No causation claims**: always display "correlation is not causation".
- **Avoid generating conclusions** when data quality is insufficient.
- **Time-window and lag assumptions visible** in the UI.
- **Correction or caution** for repeated testing where appropriate
  (e.g., note that many comparisons increase false-positive risk).

## Methods

- Non-parametric correlation (Spearman) as default (robust to non-normal data).
- Report effect size and confidence interval.
- Flag low-power results.

## Provenance

- Each correlation result records: inputs, method, sample size, window, lag,
  algorithm version.

## Limitations

- Observational, not experimental.
- Confounders are not controlled.
- Not medical advice.