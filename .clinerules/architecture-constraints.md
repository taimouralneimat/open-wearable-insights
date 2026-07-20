# Rule: Architecture Constraints

**Priority: HIGH.**

## Mandatory architecture

- **Monorepo** with the structure defined in `README.md` and
  `docs/architecture/container-view.md`. Do not deviate without an ADR.
- **Modular monolith backend**. No microservices, Kafka, Kubernetes, service
  meshes, or cloud infrastructure for the local MVP.
- **Backend**: Java 21 LTS, Spring Boot 3.5.6, Spring Modulith, Spring AI 1.0.1,
  Spring Security, OpenAPI 3, Flyway, Bean Validation, Testcontainers,
  Micrometer. Gradle Kotlin DSL.
- **Database**: PostgreSQL 16 + TimescaleDB extension. Relational + hypertable
  time-series + JSONB raw payloads. UTC storage with source TZ preserved.
  Provenance on every record. No MongoDB as primary DB without an ADR.
- **Frontend**: Flutter (web first; iOS/Android later). Riverpod, go_router,
  Dio, Freezed/json_serializable, fl_chart, flutter_secure_storage.
- **Local AI**: Ollama bound to `127.0.0.1`, default `qwen3:8b`, configurable via
  settings/env. Spring AI client. Constrained prompt. Structured validated
  output. Deterministic fallback engine.
- **Raw files**: configurable local app-data dir outside the repo; checksums in
  Postgres; storage interface swappable to S3-compatible later (no MinIO in
  initial runtime).

## Module boundaries

Backend modules (target): `identity`, `connections`, `ingestion`,
`normalization`, `provenance`, `activities`, `sleep`, `training-load`,
`readiness`, `journal`, `insights`, `coach`, `privacy`, `export`,
`administration`.

- Use Spring Modulith for module enforcement where it materially helps.
- Hexagonal/ports-and-adapters boundaries where useful; avoid ceremonial
  abstraction.
- Phase 1 implements only the modules the vertical slice needs; others get
  package stubs + backlog entries. See ADR-0005.

## Versioning

- Pin all dependency versions in `gradle/libs.versions.toml` (backend) and
  `pubspec.yaml` (Flutter).
- **No snapshots, milestones, release candidates, or abandoned dependencies.**
- Verify mutual compatibility before adopting. Record in an ADR.

## ADRs

Any change affecting module boundaries, data model, security, privacy, AI
integration, or external contracts requires an Architecture Decision Record
under `docs/architecture/adr/`. Number ADRs sequentially (0001, 0002, …).

## Diagrams

Use Mermaid or PlantUML source, not screenshots alone. Store under `docs/`.