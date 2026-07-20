# API Contracts

Shared OpenAPI 3 contracts and DTOs for Open Wearable Insights.

## Purpose

- Single source of truth for the REST API contract.
- Consumed by the Flutter client (generated DTOs) and the Spring Boot backend
  (server stubs / OpenAPI annotations).
- Ensures typed, validated communication between client and server.

## Contents (Phase 1 target)

- `openapi.yaml` — OpenAPI 3 specification.
- Generated DTOs (Flutter: via `json_serializable`/`freezed`; backend: Java records).

## Status

Phase 0: scaffold. The full contract is defined in Phase 1 when the vertical
slice endpoints are implemented.