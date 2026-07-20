# ADR-0002: Use PostgreSQL 16 + TimescaleDB as the primary database

- Status: accepted
- Date: 2026-07-18
- Deciders: Principal Solution Architect (virtual), repository owner

## Context

The product needs relational storage (accounts, devices, integrations,
journal, config), time-series storage (measurements), and JSONB (raw vendor
payloads). It must run locally, preserve provenance, and support UTC storage
with source time zone. MongoDB was considered but rejected for the primary DB.

## Decision

Use **PostgreSQL 16 with the TimescaleDB extension** (Apache 2.0 image) as the
primary database. Use hypertables for the core `measurements` table from the
start. Use JSONB for immutable raw payloads. Use explicit normalized columns
for computation.

## Consequences

- **Pros**: single database for relational + time-series + JSONB; strong
  consistency; Flyway migrations; proven; TimescaleDB is Apache 2.0.
- **Cons**: TimescaleDB adds an extension dependency; hypertables have some
  DDL constraints (no unique constraints across all columns, etc.).
- **Mitigations**: include TimescaleDB in the Docker image; use hypertables
  only where query patterns justify; document constraints in the data model.
- MongoDB as primary DB is rejected without a material requirement that
  PostgreSQL cannot satisfy.