# Story Map

> Prioritized backbone and walking-skeleton stories. Each story links to
> acceptance criteria in [`backlog.md`](./backlog.md).

## Backbone (capabilities)

1. **Identity** — local single-user auth with OIDC seam.
2. **Ingestion** — import FIT, CSV, JSON, synthetic data; dry-run; undo.
3. **Normalization** — canonical model; units; time zones; provenance.
4. **Provenance** — source, import batch, algorithm version on every record.
5. **Sleep** — duration, stages, consistency, need, data-quality.
6. **Training load** — transparent internal model; ACWR; no incomparable mixing.
7. **Readiness** — versioned, explainable, provisional-then-calibrated.
8. **Insights** — deterministic engine; optional LLM explanation; fallback.
9. **Coach** — grounded answers citing actual metrics; recommendations.
10. **Journal** — behaviors, RPE, soreness; untrusted-input treatment.
11. **Correlations** — exploratory; sample count; no causation claims.
12. **Privacy** — export, deletion, consent; loopback-only; no telemetry.
13. **Export** — complete local data export.
14. **Administration** — settings, model config, data-quality dashboard.

## Walking skeleton (Phase 1) — left to right, top to bottom

```
Identity → Ingestion → Normalization → Readiness → Insights → Coach(fallback)
   │           │             │              │           │           │
   │           │             │              │           │           └─ deterministic insight
   │           │             │              │           └─ factor contributions
   │           │             │              └─ provisional score + confidence
   │           │             └─ canonical measurements + provenance
   │           └─ synthetic data + synthetic FIT
   └─ local single-user
```

## Releases (rows)

- **Phase 0**: foundation — docs, ADRs, repo, CI skeleton.
- **Phase 1**: walking skeleton — synthetic → readiness → insight → display.
- **Phase 2**: personal Garmin import — dry-run, undo, data-quality dashboard.
- **Phase 3**: insight intelligence — baselines, factors, correlations, coach.
- **Phase 4**: mobile — iOS/Android, HealthKit, Health Connect.
- **Phase 5**: Connect IQ watch app.
- **Phase 6**: productization — hosted option, official APIs.

## Prioritized stories (Phase 1)

1. S1: Start local stack with `./scripts/dev-up.sh`
2. S2: Load synthetic wearable data
3. S3: Import synthetic FIT fixture
4. S4: Normalize to canonical model with provenance
5. S5: Compute provisional readiness score (v0.1)
6. S6: Display readiness + factor contributions
7. S7: Produce deterministic textual insight
8. S8: Optional Ollama explanation with fallback
9. S9: Automated tests pass; no real health data

See [`backlog.md`](./backlog.md) for detailed acceptance criteria.