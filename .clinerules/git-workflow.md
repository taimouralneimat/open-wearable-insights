# Rule: Git Workflow

**Priority: HIGH.**

## Branching

- `main` is the protected trunk. After initial repository bootstrap, **no direct
  commits to `main`**.
- Create feature branches from `main`:
  - `feat/<scope>-<short-desc>`
  - `fix/<scope>-<short-desc>`
  - `docs/<short-desc>`
  - `chore/<short-desc>`
  - `refactor/<scope>-<short-desc>`
  - `test/<scope>-<short-desc>`
- Keep branches short-lived and focused.

## Commits

- Use **Conventional Commits**:
  - `feat(readiness): add provisional HRV deviation factor`
  - `fix(ingestion): handle DST transition in FIT timestamps`
  - `docs(analytics): document training-load methodology`
  - `test(sleep): add golden test for stage breakdown`
  - `chore(deps): bump spring-ai to 1.0.1`
  - `refactor(coach): extract prompt sanitization`
- Keep commits atomic and well-scoped.
- Write commit messages in the imperative mood.

## Pull requests

- Keep PRs **small and reviewable**.
- Link stories and acceptance criteria in the PR description.
- Use the PR template in `.github/PULL_REQUEST_TEMPLATE.md`.
- Include the "No real health data" attestation checkbox.
- **Squash merge** unless another strategy is documented in an ADR.

## Merge gates

A PR may be merged only after **all** of the following pass:

1. Product acceptance criteria are traceable.
2. Architecture review passes when architecture is affected.
3. Automated tests pass.
4. QA issues a passing review.
5. Security issues a passing review.
6. No unresolved critical or high-severity vulnerability exists.
7. Documentation is updated.
8. No credentials, personal data, or generated local files are present.
9. The application remains runnable locally.

Virtual-role reviews (QA, Security, Architecture) are represented as
**versioned review artifacts and CI checks**, not fabricated GitHub approvals
from nonexistent people. The authenticated repository owner remains the human
approval authority.

## Pre-commit hygiene

- Run local tests before pushing.
- Ensure `.gitignore` is respected; never stage health exports, FIT files
  (except synthetic fixtures), secrets, or local config.
- If real health data is accidentally staged, **stop**, do not push, and
  contact the maintainers privately so the history can be scrubbed.