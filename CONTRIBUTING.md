# Contributing to Open Wearable Insights

First, thank you for considering a contribution. This project is a serious
portfolio product, not a disposable prototype, so we keep a small but firm set
of contribution standards.

## 1. Read the principles

Before contributing, read [`README.md`](./README.md) and
[`docs/product/vision.md`](./docs/product/vision.md). The four non-negotiable
principles — **local-first & private**, **explainable**, **product-quality
engineering**, and **health-safety boundary** — govern every contribution.

## 2. No real health data

**Never commit real health data, credentials, tokens, exports, or identifiable
information.** This includes test fixtures, screenshots, logs, issues, PRs,
and AI prompts. Development and automated testing use **synthetic or explicitly
anonymized data only**. See `.gitignore` and
[`docs/security/privacy-model.md`](./docs/security/privacy-model.md).

If you accidentally stage real health data, stop and contact the maintainers
privately so the history can be scrubbed before any push.

## 3. Development environment

See [`docs/runbooks/local-development.md`](./docs/runbooks/local-development.md).
Minimum tooling:

- Java 21 LTS
- Docker + Docker Compose
- Flutter (for client work)
- (Optional) Ollama for local AI features

## 4. Git workflow

After the initial repository bootstrap, **no direct commits to `main`**.

- Create a feature branch from `main`: `feat/<scope>-<short-desc>`,
  `fix/<scope>-<short-desc>`, `docs/<short-desc>`,
  `chore/<short-desc>`, `refactor/<scope>-<short-desc>`.
- Keep pull requests **small and reviewable**.
- Use **Conventional Commits**:

  ```
  feat(readiness): add provisional HRV deviation factor
  fix(ingestion): handle DST transition in FIT timestamps
  docs(analytics): document training-load methodology
  test(sleep): add golden test for stage breakdown
  chore(deps): bump spring-ai to 1.0.1
  refactor(coach): extract prompt sanitization
  ```

- Link stories and acceptance criteria in the PR description.
- **Squash merge** unless another strategy is documented in an ADR.

## 5. Pull-request checklist

A PR may be merged only after **all** of the following pass:

- [ ] Product acceptance criteria are traceable (link the story).
- [ ] Architecture review passes when architecture is affected (ADR updated).
- [ ] Automated tests pass (unit, module-boundary, integration, contract).
- [ ] QA review passes (represented as a versioned review artifact / CI check).
- [ ] Security review passes (represented as a versioned review artifact / CI check).
- [ ] No unresolved critical or high-severity vulnerability.
- [ ] Documentation updated.
- [ ] No credentials, personal data, or generated local files are present.
- [ ] The application remains runnable locally (`./scripts/dev-up.sh` works).

Virtual-role reviews (QA, Security, Architecture) are represented as **versioned
review artifacts and CI checks**, not fabricated GitHub approvals from
nonexistent people. The authenticated repository owner remains the human approval
authority.

## 6. Code standards

### Java / Spring Boot

- Java 21 LTS, Spring Boot 3.5.6, Spring Modulith, Spring AI 1.0.1.
- Gradle Kotlin DSL. Pin versions in `gradle/libs.versions.toml`.
- No snapshots, milestones, or release candidates.
- Hexagonal/ports-and-adapters boundaries where useful; avoid ceremonial
  abstraction.
- Bean Validation on all API inputs. OpenAPI 3 contracts.
- Flyway migrations for every schema change; never hand-edit a migrated schema.
- UTC storage; preserve source time zone and local calendar context.
- Provenance on every imported or computed record.
- Redact sensitive fields from logs; no request-body logging for health-data
  endpoints.

### Flutter

- Riverpod for state management. `go_router` for routing. Dio for HTTP.
- Freezed + `json_serializable` for typed contracts.
- `fl_chart` for charts (document any swap in an ADR).
- `flutter_secure_storage` for mobile secrets.
- Widget tests for critical screens; golden tests for a limited set of stable
  screens; responsive-layout and accessibility checks.

### Tests

- Unit, module-boundary, repository integration (Testcontainers), import-parser,
  idempotency, golden analytics, API contract, security, LLM schema/fallback.
- Flutter: unit, widget, golden, integration, responsive, accessibility,
  empty/calibration/stale/partial-data/error states.
- No real health data in any test fixture.

## 7. Architecture decisions

Any change that affects module boundaries, data model, security, privacy, AI
integration, or external contracts requires an **Architecture Decision Record**
under [`docs/architecture/adr/`](./docs/architecture/adr/). Use the ADR template
therein.

## 8. Security & privacy review

If your change touches authentication, authorization, secrets, logging, import
validation, LLM prompts, or data export/deletion, request a security review in
the PR. See [`SECURITY.md`](./SECURITY.md) and
[`docs/security/threat-model.md`](./docs/security/threat-model.md).

## 9. Licensing

By contributing, you agree that your contributions are licensed under the
Apache License 2.0, as described in [`LICENSE`](./LICENSE).