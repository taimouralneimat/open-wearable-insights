# ADR-0005: Phased module implementation with package stubs

- Status: accepted
- Date: 2026-07-18
- Deciders: Principal Solution Architect (virtual), repository owner

## Context

The target backend has 15 modules: `identity`, `connections`, `ingestion`,
`normalization`, `provenance`, `activities`, `sleep`, `training-load`,
`readiness`, `journal`, `insights`, `coach`, `privacy`, `export`,
`administration`. Implementing all 15 from day one risks ceremonial abstraction
and delays the vertical slice.

## Decision

Lay out the package structure for all 15 target modules now. **Implement** only
the modules the Phase-1 vertical slice needs: `identity` (local auth stub),
`ingestion`, `normalization`, `provenance`, `sleep` (partial), `readiness`,
`insights`, `coach`, `privacy` (partial), `export` (partial). The remaining
modules (`connections`, `activities`, `training-load`, `journal`,
`administration`) get package stubs + backlog entries.

## Consequences

- **Pros**: faster vertical slice; less ceremony; clear backlog for remaining
  modules; Spring Modulith verification still applies to implemented modules.
- **Cons**: stubs may need rework when activated; module boundaries must be
  respected even for stubs.
- **Mitigations**: each stub has a `package-info.java` documenting its purpose
  and backlog story; Spring Modulith tests guard boundaries.