# Security Policy

## Supported versions

Open Wearable Insights is in active development. Security fixes are applied to
the `main` branch and the latest release tag.

| Version | Supported |
|---|---|
| latest `main` | ✅ |
| latest release tag | ✅ |
| older releases | ❌ |

## Reporting a vulnerability

We take security and privacy vulnerabilities seriously. If you believe you have
found a security or privacy issue, **please do not open a public GitHub issue**.

Report vulnerabilities privately using one of these methods:

1. **Preferred — GitHub Security Advisories (private vulnerability reporting):**
   - Go to https://github.com/taimour-dev/open-wearable-insights/security/advisories/new
   - Click "Report a vulnerability"
   - Provide a clear description, reproduction steps, and impact assessment.

2. **Email fallback** (if private reporting is unavailable):
   - Contact the repository owner via the email listed on the GitHub profile.
   - Encrypt the report if it contains sensitive details.

Please include:
- A description of the issue and its privacy/security impact.
- Affected component (backend, Flutter client, Connect IQ app, import pipeline,
  local AI integration, etc.).
- Steps to reproduce or a proof of concept.
- Suggested mitigation if you have one.

## Response process

- **Acknowledgement:** within 5 business days.
- **Initial assessment:** within 14 business days.
- **Fix coordination:** we will work with you on disclosure timing and credit.
- Valid privacy-impacting issues (e.g., health data leakage, secret exposure,
  prompt-injection bypass) are treated as high priority.

## Security & privacy commitments

This project follows secure-by-default controls:

- Local services bind to **loopback only** by default.
- PostgreSQL and Ollama are **never exposed publicly** in the local edition.
- **Spring Security** is active even in local mode.
- OAuth refresh tokens and integration secrets are **encrypted at rest**;
  encryption keys are kept **outside the database** (OS keychain where practical).
- Sensitive fields are **redacted from logs**; request-body logging is disabled
  for health-data endpoints.
- All imported files are **validated**; file-size limits, archive-traversal and
  decompression-bomb defenses, and CSV formula-injection protections are in place.
- LLM output is **validated** against a schema; journal content is treated as
  **prompt-injection-capable** untrusted input.
- Consent, export, and deletion flows are provided.
- An **SBOM** (CycloneDX) is generated; containers and dependencies are scanned
  (Trivy, Gitleaks, Semgrep/CodeQL where compatible).

## What is out of scope

- This is a **wellness and fitness analytics** product, not a medical device.
  Issues about lack of medical diagnosis are not security vulnerabilities.
- Vulnerabilities in third-party dependencies should be reported upstream and
  will be tracked here via Dependabot/Trivy.
- Self-reported issues requiring access to real health data cannot be triaged
  without the reporter's explicit consent and must not include real health data.

## No real health data

**Never include real health data, credentials, tokens, or exports in a security
report unless explicitly requested and necessary.** Synthetic or anonymized
reproducers are strongly preferred.