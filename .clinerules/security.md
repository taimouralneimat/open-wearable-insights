# Rule: Security Standards

**Priority: HIGH.**

## Secure-by-default controls

- Bind local services to **loopback only** by default (`127.0.0.1`).
- Do not expose PostgreSQL or Ollama publicly.
- Use **Spring Security** even in local mode.
- Design single-user local authentication so it can later evolve to OIDC.
- Encrypt stored OAuth refresh tokens and sensitive integration secrets.
- Keep encryption keys **outside the database**; prefer the OS keychain where
  practical.
- Use secure mobile storage (`flutter_secure_storage`).
- Redact sensitive fields from logs.
- **Disable request-body logging for health-data endpoints.**
- Validate all imported files; set file-size limits.
- Defend against archive traversal and decompression bombs.
- Protect against CSV formula injection in exports.
- Use prepared statements or safe persistence APIs (JPA/Spring Data).
- Validate LLM output against a schema.
- Treat journal content as **prompt-injection-capable** untrusted input.
- Provide consent, export, and deletion flows.
- Generate an SBOM (CycloneDX).
- Scan containers and dependencies (Trivy).
- Run secret scanning (Gitleaks).
- Add a public vulnerability-reporting process through `SECURITY.md`.

## Tooling

- CodeQL or Semgrep for static analysis.
- Gitleaks for secret scanning.
- Trivy for container/dependency scanning.
- OWASP dependency-check where compatible.
- CycloneDX for SBOM generation.

## Prohibitions

- Do not claim formal regulatory certification or compliance that has not been
  independently assessed.
- Do not hard-code secrets, tokens, or API keys in source.
- Do not log full request bodies for health-data endpoints.
- Do not allow the LLM to execute arbitrary database queries.
- Do not trust LLM output without schema validation.

## Review gate

Security review is an **independent gate**. No role may declare its own work
approved. Security review is represented as a versioned review artifact and a
CI check, not a fabricated GitHub approval.