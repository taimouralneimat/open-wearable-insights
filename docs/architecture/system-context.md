# System Context

```mermaid
graph TB
  User([User — Taimour<br/>Garmin watch owner])

  subgraph System[Open Wearable Insights — local]
    App[Flutter web/iOS/Android client]
    API[Spring Boot API<br/>modular monolith]
    DB[(PostgreSQL + TimescaleDB)]
    Files[Local app-data dir<br/>raw files outside repo]
    Ollama[(Ollama 127.0.0.1)]
  end

  Garmin[Garmin device<br/>produces FIT files]
  GarminConnect[Garmin Connect export<br/>CSV/JSON/FIT — user-provided]
  CSV[Generic CSV]
  Journal[Manual journal input]

  User -->|imports| Garmin
  User -->|exports| GarminConnect
  User -->|enters| Journal
  Garmin -->|FIT| Files
  GarminConnect -->|CSV/JSON/FIT| Files
  CSV -->|CSV| Files

  User -->|uses| App
  App -->|REST/OpenAPI| API
  API -->|read/write| DB
  API -->|stores checksums/metadata| DB
  API -->|reads raw| Files
  API -->|constrained prompt| Ollama
  Ollama -->|structured output| API
```

## Boundaries

- **In scope**: local ingestion, normalization, storage, analytics, insights,
  coaching, Flutter client, local LLM integration.
- **Out of scope (Phase 0–1)**: official Garmin Health/Activity/Training APIs,
  WHOOP API, Apple HealthKit, Android Health Connect, Connect IQ app, hosted
  multi-user deployment. These have interface seams but are not implemented.
- **Never in scope**: unofficial Garmin Connect password scraping; medical
  diagnosis; copying proprietary formulas/UI.

## External actors

- **User**: imports data, views insights, asks coach, manages privacy.
- **Garmin device**: produces FIT files (via file import, not live API in MVP).
- **Ollama (local)**: optional local LLM; loopback only.

## Trust boundaries

- All services bind to `127.0.0.1`.
- No inbound ports from the network except the loopback API.
- Raw files live outside the repo; only checksums/metadata in DB.
- LLM receives only a constrained, structured summary — never raw DB access.