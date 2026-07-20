# Runbook — Backup and Restore

> Local-first: backups are local. No cloud required.

## What to back up

1. **PostgreSQL data** — all normalized measurements, derived metrics,
   provenance, journal entries, config.
2. **Raw files** — the local app-data directory containing imported source
   files (outside the repo).

## Backup procedure

### PostgreSQL

Use `pg_dump` against the local Postgres (loopback only):

```bash
# Ensure the stack is running
./scripts/dev-up.sh

# Dump the database (example)
docker exec -i open-wearable-insights-postgres \
  pg_dump -U owi -d owi > backup/owi-$(date +%Y%m%d-%H%M%S).sql
```

- Store backups **outside the repo** (e.g., `~/.open-wearable-insights/backups/`).
- Backups are gitignored.

### Raw files

Copy the local app-data directory:

```bash
cp -R ~/.open-wearable-insights/imports/ \
  ~/.open-wearable-insights/backups/imports-$(date +%Y%m%d-%H%M%S)/
```

## Restore procedure

### PostgreSQL

```bash
# Ensure the stack is running
./scripts/dev-up.sh

# Restore from a dump
cat backup/owi-YYYYMMDD-HHMMSS.sql | \
  docker exec -i open-wearable-insights-postgres psql -U owi -d owi
```

### Raw files

```bash
cp -R ~/.open-wearable-insights/backups/imports-YYYYMMDD-HHMMSS/ \
  ~/.open-wearable-insights/imports/
```

## Verification

After restore:
- Open the dashboard; data should populate.
- Run `./scripts/load-synthetic.sh` is **not** needed (real/saved data is
  restored).
- Verify provenance and import batches are intact.

## Data export (user right)

Use Privacy → Export in the UI for a complete local data export (CSV/JSON).

## Data deletion (user right)

Use Privacy → Delete in the UI for complete local data deletion. This removes
all records from PostgreSQL and the raw files directory.

## No real health data in backups committed to the repo

- Backups live outside the repo and are gitignored.
- Never commit a backup containing real health data.