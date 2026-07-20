# Runbook — Importing Data

> **Never place real health data inside the repository.** Import from a local
> app-data directory **outside** the repo. The repo only stores checksums and
> metadata in PostgreSQL.

## Local import folder

Configure a local app-data directory outside the repo, e.g.:

```
~/.open-wearable-insights/imports/
```

Set the path in `application.yml` or via environment variable:

```
OWI_IMPORT_DIR=~/.open-wearable-insights/imports
```

## Supported sources (Phase 1–2)

- **Synthetic data** (`packages/test-data`) — for development/testing only.
- **Garmin FIT files** — via the Garmin FIT SDK.
- **Garmin Connect export** — supported JSON, CSV, FIT files.
- **Generic CSV** — selected daily health measurements.
- **Manual journal entry** — perceived exertion, behaviors, notes.

## Import workflow

1. Place files in the configured local import directory.
2. Open the Import UI (or run the CLI loader).
3. **Dry-run validation** shows:
   - Supported files.
   - Duplicate detection (content hash).
   - Unsupported-record reporting.
   - Import summary.
4. Confirm the import.
5. Progress is reported; errors are explicit; no silent drops.
6. Provenance (source, import batch, algorithm version) is recorded.

## Idempotency

- Re-importing the same file (same content hash) is a no-op.
- Duplicates are reported, not silently merged.

## Undo

- An import batch can be undone.
- Undo deletes all measurements and derived metrics from that batch.
- Raw payloads may be retained (configurable) or deleted.

## Time zones & DST

- Source time zone is preserved per measurement.
- UTC storage; no silent timezone coercion.
- DST transitions are handled explicitly.

## No real health data in the repo

- `.gitignore` excludes `*.fit` (except `synthetic*.fit` under
  `packages/test-data`), health exports, DB files, Docker volumes.
- CI health-data guard rejects non-synthetic FIT files.
- If real health data is accidentally staged, **stop**, do not push, and
  contact the maintainers privately so the history can be scrubbed.