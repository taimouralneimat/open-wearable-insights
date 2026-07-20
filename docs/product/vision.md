# Product Vision

## Why this product exists

Wearable devices capture rich physiological and activity data, but the
interpretation layer — recovery, strain, sleep, coaching, and behavioral
insights — is often locked behind proprietary scores, cloud dependencies, or
opaque algorithms. Users who value privacy, transparency, and ownership of
their own data have no serious option that combines detailed Garmin-grade data
capture with WHOOP-grade interpretation, locally.

**Open Wearable Insights** exists to close that gap.

## Vision statement

A privacy-first, local-first wearable analytics product that turns detailed
health and activity data into **understandable, personalized, transparent, and
useful** recovery, sleep, training-load, and coaching insights — explainable by
design, useful without a cloud, and evolvable into a real multi-user product
later.

## Target user (first customer)

The first customer is the developer: a Garmin watch user (likely Forerunner
family) who previously used WHOOP and wants deeper, more transparent, and more
private analytics than either vendor offers. The first deployment runs privately
and locally on a personal computer.

## Principles (non-negotiable)

1. **Local-first and private.** All core functionality works locally. No health
   data leaves the machine unless explicitly enabled. No hosted telemetry. The
   app remains useful when the LLM is unavailable.
2. **Explainable rather than mysterious.** Scores are deterministic, versioned,
   and testable. The LLM explains; it never invents. Every score exposes inputs,
   provenance, baseline, factor contributions, missing-data treatment,
   confidence, algorithm version, plain-language explanation, and limitations.
3. **Product-quality engineering.** Clean boundaries, automated migrations,
   typed contracts, automated tests, security controls, CI, ADRs, observable
   analytics, original visual design, proper documentation, incremental PRs.
4. **Health-safety boundary.** Wellness/fitness analytics, not a medical device.
   No diagnosis, medication recommendations, or medical-treatment directives.

## What success looks like

- I can import my Garmin data locally and see a transparent readiness score with
  factor contributions and a plain-language explanation.
- I can ask "why is my readiness lower today?" and get an answer grounded in my
  actual metrics — not a black-box justification.
- The app works fully when Ollama is unavailable, via a deterministic fallback.
- No real health data is ever in the repository, logs, or prompts.
- The architecture is clean enough to evolve into a hosted multi-user product
  without compromising local-first operation.

## Non-goals

- Reproducing any vendor's proprietary formula, score, or visual design.
- Medical diagnosis or treatment.
- Replacing Garmin Connect as a data-capture surface.
- Cloud-first operation. Local-first is the default, not a degraded mode.
- Supporting every platform immediately. Phased delivery begins with a local
  vertical slice.

## Competitive positioning

This product does not claim parity with any vendor's proprietary algorithm. It
aims to provide **transparent, original** analytics that are explainable and
useful, using the user's own data, locally. See
[`parity-matrix.md`](./parity-matrix.md) for the ongoing competitive analysis.

## Evolution path

1. **Phase 0–1**: Local vertical slice with synthetic data + synthetic FIT.
2. **Phase 2**: Personal Garmin import (no real data in repo).
3. **Phase 3**: Insight intelligence — baselines, factors, correlations, coach.
4. **Phase 4**: Mobile apps (iOS/Android, HealthKit, Health Connect).
5. **Phase 5**: Garmin Connect IQ watch app.
6. **Phase 6**: Optional hosted architecture, preserving local-first operation.

See [`release-plan.md`](./release-plan.md).