# ADR-0001: Use Gradle Kotlin DSL for the backend build

- Status: accepted
- Date: 2026-07-18
- Deciders: Principal Solution Architect (virtual), repository owner

## Context

The backend is a Java 21 / Spring Boot modular monolith. We need a build tool
that supports Spring Modulith, Testcontainers, Spring AI, Flyway, OpenAPI 3,
Micrometer, and CycloneDX SBOM generation. The two mainstream options are
Gradle (Kotlin DSL) and Maven.

## Decision

Use **Gradle with Kotlin DSL** (`build.gradle.kts`, `settings.gradle.kts`,
`gradle/libs.versions.toml`).

## Consequences

- **Pros**: type-safe DSL, fast incremental builds, good Spring Boot plugin
  support, concise multi-module configuration, easy version catalog.
- **Cons**: slightly steeper learning curve than Maven for contributors only
  familiar with Maven; Kotlin DSL compile errors can be verbose.
- **Mitigations**: use the Gradle wrapper (no system install required); pin
  versions in `libs.versions.toml`; document common tasks in the runbook.
- This decision is reversible (Maven migration is possible) but not planned.