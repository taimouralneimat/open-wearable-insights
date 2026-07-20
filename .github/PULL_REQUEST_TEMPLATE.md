# Pull Request

## Summary

<!-- Brief description of what this PR changes and why. -->

## Linked story & acceptance criteria

<!-- Link the story/issue and list the acceptance criteria addressed. -->

- Story: 
- Acceptance criteria addressed: 

## Type of change

- [ ] feat (new feature)
- [ ] fix (bug fix)
- [ ] docs (documentation)
- [ ] refactor
- [ ] test
- [ ] chore / deps
- [ ] breaking change

## Architecture impact

- [ ] No architecture impact.
- [ ] Architecture impact — ADR added/updated: 
<!-- If module boundaries, data model, security, privacy, AI integration, or
     external contracts changed, an ADR is required. -->

## Merge-gate checklist

- [ ] Product acceptance criteria are traceable.
- [ ] Architecture review passes when architecture is affected.
- [ ] Automated tests pass (unit, module-boundary, integration, contract).
- [ ] QA review passes (versioned review artifact / CI check).
- [ ] Security review passes (versioned review artifact / CI check).
- [ ] No unresolved critical or high-severity vulnerability.
- [ ] Documentation updated.
- [ ] The application remains runnable locally (`./scripts/dev-up.sh` works).

## Privacy & data-safety attestation (REQUIRED)

- [x] **No real health data** — I attest that this PR contains **no** real
      health data, credentials, tokens, exports, identifiable information, or
      real-data screenshots. All test fixtures are synthetic or explicitly
      anonymized.
- [x] No secrets, API keys, or local configuration files are included.
- [x] No FIT files are included except synthetic fixtures under
      `packages/test-data/**/synthetic*.fit`.

## Health-safety boundary

- [ ] This change does not introduce medical diagnosis, medication
      recommendations, or medical-treatment directives.

## Notes for reviewers

<!-- Anything reviewers should pay attention to, risky areas, follow-ups. -->