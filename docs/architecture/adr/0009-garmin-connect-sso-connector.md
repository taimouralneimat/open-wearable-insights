# ADR-0009: Real Garmin Connect login for historical data backfill

- Status: accepted, implemented
- Date: 2026-08-02
- Deciders: repository owner

## Context

The `WearableConnector` interface (ADR-0006) and `GarminFitConnector` solve
*file* ingestion — the user drops a `.fit`/export file in a local folder and
the app parses it. That's the right model for one-off exports, but it can't
answer the actual product goal: pulling a Garmin user's *history* — months
of sleep, HRV, stress, resting HR — into the app automatically.

Three paths were evaluated for that goal:

1. **Garmin's official Health API.** Requires a registered legal entity and
   is closed to new individual-developer applicants. Not available to this
   project.
2. **USB / Garmin Express.** Initially attempted, then rejected. Garmin
   Express's local sync folder (`GarminExpressLocator`, still present for
   opportunistic recent-file discovery) is a *transient upload buffer* —
   files are cleared once pushed to Garmin's cloud. Verified live against a
   real device: most wellness categories were already empty by the time this
   was inspected, because Express had already finished uploading them. It
   structurally cannot deliver history, only whatever hasn't synced yet.
3. **Garmin Connect's unofficial mobile-app API**, using the user's own
   real login credentials — the same mechanism Garmin's own mobile app uses,
   reverse-engineered by the community. This is a ToS gray area (Garmin does
   not publish this API for third-party use), explicitly accepted by the
   repository owner as the tradeoff for the only path that actually reaches
   historical data without a legal entity.

Path 3 was chosen. This ADR covers what makes it a fundamentally different
kind of connector from `WearableConnector`, not a variant of it.

## Decision

Build `garminconnect` as its own module (`services/api/.../garminconnect/`),
separate from `connections`/`ingestion`, because the shape of the problem is
different in kind, not degree:

| | `WearableConnector` (ADR-0006) | `garminconnect` |
|---|---|---|
| Input | A local file the user already has | A live account the app authenticates into |
| Trust boundary | File the user placed on disk | The user's real password, in transit once |
| I/O | Parse bytes | Real HTTPS calls to Garmin's servers, with real state (tokens, MFA, rate limits) |
| Failure modes | Malformed/unsupported file | Wrong password, MFA challenge, Garmin rate-limiting, Garmin changing its API |
| Persistence beyond the parsed data | None | OAuth tokens, connection status |

Concretely:

- **`GarminConnectAuthClient`** is a from-scratch Java port of Garmin's
  unofficial mobile-app SSO/OAuth2 login flow — a plain `java.net.http`
  POST to `sso.garmin.com/mobile/api/login`, MFA support, and a CAS
  service-ticket → DI OAuth2 bearer-token exchange
  (`diauth.garmin.com`). Modeled on the actively-maintained reference
  implementation `cyberjunky/python-garminconnect` (its predecessor,
  `matin/garth`, is deprecated — Garmin changed its auth flow and garth's
  maintainer stopped adapting). That reference library actually runs a
  5-strategy cascading fallback chain; 3 of those strategies exist purely to
  rotate TLS fingerprints past Cloudflare bot detection via `curl_cffi`,
  which the JVM's TLS stack cannot replicate without a custom SSL engine.
  This client implements only the plain-HTTP strategy — live-verified
  against Garmin's real login endpoint (a real request with a fake account
  correctly returned `INVALID_USERNAME_PASSWORD`, proving a plain JVM
  `HttpClient` is accepted, not blocked on TLS fingerprint alone). If that
  changes, the non-cffi fallback strategy or true TLS impersonation would be
  the next step — not attempted, since it isn't yet known to be necessary.
- **Only OAuth tokens are ever persisted** (`garmin_connect_account` table,
  V07 migration) — never the password. The password is used exactly once,
  in-memory, to build the login request.
- **`GarminConnectSyncService`** reuses `MeasurementRepository`/
  `ImportBatchRepository` from `ingestion` (now exposed as a Modulith
  `@NamedInterface` for this reason) rather than duplicating
  provenance/measurement persistence — the *output* shape (measurements +
  import batch) is identical to a file-based import; only how the data
  arrives differs. `source = 'vendor_api'` distinguishes it from
  file-sourced batches.
- **Deliberately throttled**: ~300ms between days, stops early after 3
  consecutive rate-limited days. Historical backfill means dozens to
  hundreds of real requests to Garmin's servers in one sync; hammering them
  risks getting the user's real account rate-limited or flagged, which is a
  materially worse outcome than a slower sync.
- **No credential handling outside the app UI**: login (with MFA), sync
  triggering, and account deletion are all real, user-triggered actions
  through the Garmin Connect screen (Import → Garmin Connect) — never
  performed on the user's behalf via terminal/script, including during this
  feature's own development and verification.

## Consequences

- **Pros**: the only path in this app that reaches actual Garmin history
  without a legal entity; reuses the existing measurement pipeline rather
  than forking it; token-only persistence keeps the password exposure
  window to a single in-memory use.
- **Cons**: unofficial API — Garmin can change or block this flow at any
  time without notice, unlike the official-SDK-backed `WearableConnector`
  path (ADR-0006). ToS gray area, explicitly accepted by the repository
  owner. No TLS-fingerprint evasion, so a future Cloudflare tightening could
  break login without warning.
- **Mitigations**: tokens only, never the password, persisted; sync is
  throttled and backs off on sustained rate-limiting rather than retrying
  blindly; the auth client's response-parsing logic is unit-tested against
  fixture JSON so a real Garmin response-shape change is easy to diagnose
  without needing to reproduce it live.

## Status update (2026-08-03): Body Battery as a real readiness factor

The sync also pulls Garmin-exclusive signals `WearableConnector`/FIT never
exposed — Body Battery, Garmin's own proprietary Training Readiness score,
detailed stress, calories, floors, intensity minutes — at zero extra API
cost (already present in the daily-summary response, previously only 3 of
its ~20 fields were read).

Displaying these next to the app's own readiness score is data replication,
not differentiation — Garmin's own app already shows them. `ReadinessCalculator`
v0.2 instead wires Body Battery in as a real, weighted, explainable factor
(personal-baseline deviation, same approach as HRV/RHR), funded by
reallocating weight from Stress (Body Battery is Garmin's own richer,
multi-signal version of the same underlying signal). Garmin's Training
Readiness score is kept separate for comparison, deliberately not blended
in — see `GarminEnrichmentService` and parity-matrix.md row 1.
