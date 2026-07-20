# ADR-0008: Single-user local authentication with an OIDC seam

- Status: accepted
- Date: 2026-07-18
- Deciders: Application Security & Privacy Lead (virtual), Principal Solution Architect (virtual), repository owner

## Context

The first deployment is single-user and local. We still need Spring Security active
even in local mode. The architecture must later evolve to multi-user OIDC without
a rewrite.

## Decision

Implement **single-user local authentication** now, behind a Spring Security
configuration that can later evolve to OIDC. Use a local credential or token
stored securely (OS keychain where practical). Never hard-code secrets. Keep
the auth interface abstract enough that an OIDC provider can replace the local
auth without touching business modules.

## Consequences

- **Pros**: security is active from day one; clear evolution path to OIDC;
  no business-module coupling to auth specifics.
- **Cons**: local single-user auth is a simplification; must be clearly
  documented as not production-grade for multi-user hosted use.
- **Mitigations**: document the evolution path in this ADR; Phase 6 adds OIDC,
  tenant isolation, and cloud secrets; never expose the local API publicly.