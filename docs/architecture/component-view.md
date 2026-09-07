# Component View

```mermaid
graph TB
  subgraph API[Spring Boot API — modular monolith]
    Identity[identity<br/>local single-user auth · OIDC seam]
    Connections[connections<br/>integration config · encrypted secrets]
    Ingestion[ingestion<br/>FIT/CSV/JSON parsers · dry-run · undo]
    Normalization[normalization<br/>canonical model · units · TZ]
    Provenance[provenance<br/>source · batch · algo version]
    Activities[activities]
    Sleep[sleep]
    TrainingLoad[training-load<br/>ACWR · TRIMP · HR-zone load]
    Readiness[readiness<br/>versioned · provisional → calibrated]
    Journal[journal<br/>untrusted input]
    Insights[insights<br/>deterministic engine]
    Coach[coach<br/>RestClient → Ollama · fallback]
    Privacy[privacy<br/>export · delete · consent]
    Export[export<br/>complete local export]
    Administration[administration<br/>settings · model config]
    Shared[shared<br/>common · errors · constants]
  end

  DB[(PostgreSQL + TimescaleDB)]
  Raw[Local app-data dir]
  Ollama[(Ollama 127.0.0.1)]

  Ingestion -->|raw payloads| DB
  Ingestion -->|files| Raw
  Ingestion --> Normalization
  Normalization --> Provenance
  Normalization --> DB
  Provenance --> DB
  Sleep --> DB
  TrainingLoad --> DB
  Readiness --> DB
  Insights --> Readiness
  Insights --> Sleep
  Insights --> TrainingLoad
  Coach --> Insights
  Coach -->|constrained prompt| Ollama
  Ollama -.->|structured output| Coach
  Privacy --> DB
  Export --> DB
  Administration --> DB
```

## Module status (Phase 0–1)

| Module | Phase 1 | Notes |
|---|---|---|
| identity | stub + local auth | single-user; OIDC seam |
| connections | ✅ implemented | `WearableConnector` + `ConnectorRegistry`; Garmin FIT first (ADR-0006) |
| ingestion | ✅ implemented | FIT (via connections)/CSV/JSON/synthetic |
| normalization | ✅ implemented | canonical model |
| provenance | ✅ implemented | source/batch/algo version |
| activities | ✅ implemented (backend) | day-level step rollup + real session parsing (ADR-0006); no Flutter UI yet |
| sleep | partial | Phase 1 summary; Phase 3 deep |
| training-load | stub | Phase 3 |
| readiness | ✅ implemented | v0.1 provisional |
| journal | stub | Phase 3 |
| insights | ✅ implemented | deterministic engine |
| coach | ✅ implemented | RestClient → Ollama + fallback (ADR-0003 status update) |
| privacy | partial | export/delete Phase 1–2 |
| export | partial | Phase 1–2 |
| administration | stub | settings |

## Hexagonal layering (where useful)

Each module may follow:
- `domain/` — entities, value objects.
- `application/` — application services, ports.
- `adapter/in/` — REST controllers.
- `adapter/out/` — persistence, external clients.

Trivial modules avoid ceremonial abstraction.