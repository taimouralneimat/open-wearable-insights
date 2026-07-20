# Rule: Testing Standards

**Priority: HIGH.**

## General

- No real health data in any fixture. Use `packages/test-data` synthetic
  generator and `synthetic*.fit` fixtures only.
- Tests must be deterministic. No reliance on wall-clock time, network, or
  external services beyond Testcontainers-managed PostgreSQL/TimescaleDB.
- Golden tests pin known-good analytics outputs; failures require an ADR-level
  methodology change, not a silent rebaseline.

## Backend (services/api)

- JUnit 5 + AssertJ + Mockito + Spring Modulith tests.
- Testcontainers for repository integration tests (PostgreSQL + TimescaleDB).
- Module-boundary tests via Spring Modulith verification.
- Import-parser tests, idempotency tests, API contract tests, security tests.
- Golden analytics tests for readiness/sleep/training-load.
- LLM schema validation tests and deterministic fallback tests.
- Malformed-LLM-response tests.

## Flutter (apps/flutter)

- Unit tests for logic and providers.
- Widget tests for critical screens.
- Golden tests for a limited number of stable critical screens.
- Integration tests for key flows.
- Responsive-layout tests (mobile, tablet, web breakpoints).
- Accessibility checks (semantic labels, contrast, keyboard nav).
- State coverage: empty, loading, calibration, stale, partial-data, error,
  populated.

## Garmin Connect IQ (apps/garmin-connect-iq)

- Compilation in CI.
- Static checks available through the SDK.
- Simulator smoke tests where automation is practical.
- Device-capability tests.

## System / end-to-end

- End-to-end local vertical-slice test.
- Backup and restore test.
- Data export and deletion test.
- Import undo test.
- No-network mode test.
- LLM-disabled mode test.
- Malformed LLM response test.
- Duplicate-import test.
- Time-zone boundary test (including DST transitions).

## CI gates

- All tests must pass before merge.
- Coverage thresholds enforced where meaningful (do not chase 100% blindly).
- Health-data guard rejects any `*.fit` outside
  `packages/test-data/**/synthetic*.fit` and `apps/garmin-connect-iq/**`.
- Gitleaks, Trivy, and SBOM generation run on every PR.