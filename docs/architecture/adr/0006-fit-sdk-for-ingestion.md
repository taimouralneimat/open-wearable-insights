# ADR-0006: Use the Garmin FIT SDK for FIT file ingestion

- Status: accepted
- Date: 2026-07-18
- Deciders: Principal Backend Engineer (virtual), repository owner

## Context

The product must import Garmin FIT files as a first-class feature (not a hack).
We need an official, license-permissive SDK to parse FIT files reliably across
device types and firmware versions.

## Decision

Use the **Garmin FIT SDK** (Java) for parsing `.fit` files in the `ingestion`
module. Verify the exact license terms at implementation time and record them
in `NOTICE`. Only synthetic fixtures (`synthetic*.fit` under
`packages/test-data`) are used in development and testing.

## Consequences

- **Pros**: official SDK; reliable parsing; supports all FIT message types.
- **Cons**: SDK license must be verified and attributed; SDK updates may lag
  behind new device features.
- **Mitigations**: record license in `NOTICE`; isolate SDK usage behind an
  ingestion adapter interface; never commit real FIT files.
- FIT import remains a first-class feature, not a temporary hack.