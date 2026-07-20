# Rule: Java / Spring Boot Standards

**Priority: HIGH.**

## Stack (pinned)

- Java 21 LTS (LTS only).
- Spring Boot 3.5.6, Spring Modulith, Spring AI 1.0.1, Spring Security.
- OpenAPI 3 (springdoc-openapi), Flyway, Bean Validation (jakarta.validation),
  Testcontainers, Micrometer.
- Gradle Kotlin DSL. Versions pinned in `gradle/libs.versions.toml`.
- **No snapshots, milestones, release candidates, or abandoned dependencies.**

## Project layout (services/api)

```
services/api/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/libs.versions.toml
└── src/
    ├── main/
    │   ├── java/com/openwearableinsights/api/
    │   │   ├── identity/        connections/   ingestion/
    │   │   ├── normalization/   provenance/     activities/
    │   │   ├── sleep/           trainingload/   readiness/
    │   │   ├── journal/         insights/       coach/
    │   │   ├── privacy/         export/         administration/
    │   │   └── shared/
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       └── db/migration/      # Flyway V<nn>__*.sql
    └── test/...
```

Each module folder follows hexagonal layering where useful:
`domain/`, `application/`, `adapter/in/`, `adapter/out/`. Avoid ceremonial
abstraction for trivial modules.

## Coding rules

- Package-by-module; cross-module access only via explicitly exposed
  application services / ports. Use Spring Modulith verification tests.
- All API inputs validated with Bean Validation. All endpoints documented via
  OpenAPI 3.
- Records for DTOs and value objects where appropriate. `Optional` for return
  types, never for parameters.
- Prefer constructor injection. No field injection.
- UTC storage; preserve source time zone and local calendar context.
- Provenance on every imported or computed record (source, import batch,
  algorithm version).
- Idempotency keys and content hashes for imports.
- Versioned derived metrics (algorithm version column).
- Never hand-edit a migrated schema — add a new Flyway migration.
- Redact sensitive fields from logs. **No request-body logging for health-data
  endpoints.**
- LLM output validated against a schema; journal content treated as
  prompt-injection-capable untrusted input.
- Deterministic fallback engine for all LLM-driven features.

## Testing

- JUnit 5 + AssertJ + Mockito + Spring Modulith tests.
- Testcontainers for repository integration tests (PostgreSQL + TimescaleDB).
- Golden analytics tests for readiness/sleep/training-load.
- Import-parser tests, idempotency tests, API contract tests, security tests,
  LLM schema/fallback tests.
- No real health data in any fixture.