# Wireframe — Dashboard (web)

> Low-fidelity ASCII spec. Original visual language.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  ☰  Open Wearable Insights            [LLM: ● available / ○ fallback]  ⚙    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌───────────────────────┐  ┌────────────────────────────────────────────┐  │
│  │   READINESS   72      │  │  TOP FACTORS                               │  │
│  │   ● provisional       │  │  ▲ HRV +12%   ▼ RHR -4%   ▼ Sleep -8%      │  │
│  │   confidence: low     │  │  ▲ Stress +3%   ◐ data completeness 60%    │  │
│  │   algo v0.1           │  │                                            │  │
│  │   [drill into →]      │  │  [why is my readiness lower today? →]      │  │
│  └───────────────────────┘  └────────────────────────────────────────────┘  │
│                                                                             │
│  ┌──────────────────────────────┐  ┌─────────────────────────────────────┐  │
│  │  SLEEP — last night          │  │  TRAINING LOAD                      │  │
│  │  7h 12m   efficiency 88%     │  │  ACWR 1.2   acute 312  chronic 260 │  │
│  │  stages: ▓▓▓▓ Deep           │  │  [view →]                           │  │
│  │          ▓▓▓ REM             │  │                                     │  │
│  │          ▓▓▓ Light            │  │                                     │  │
│  └──────────────────────────────┘  └─────────────────────────────────────┘  │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │  DAILY COACH (deterministic)                                           │ │
│  │  "Your readiness is provisional. HRV is above baseline, but sleep      │ │
│  │   duration was short. Consider an easy session today."                 │ │
│  │  [Ask the coach →]                                                     │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
│  ⚠ Data-quality: 2 metrics missing for today (stress, SpO2).               │
└─────────────────────────────────────────────────────────────────────────────┘
```

## States

- **Empty**: "No data yet. Run `./scripts/load-synthetic.sh` or import data."
- **Loading**: skeleton cards with shimmer.
- **Calibration**: readiness card shows "● provisional" badge.
- **Stale**: data-quality banner replaces the warning line.
- **Partial**: missing metrics shown as "◐" with names.
- **Error**: "Couldn't load dashboard. [retry]".
- **Populated**: as above.