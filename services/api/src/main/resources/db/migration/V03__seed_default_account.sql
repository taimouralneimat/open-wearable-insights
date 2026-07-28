-- Phase 2: seed the default local single-user account.
-- ADR-0008: single-user local auth with OIDC seam.
-- The ImportService uses DEFAULT_ACCOUNT_ID = 1L; this row must exist
-- or every import_batches insert violates the account_id FK.
-- The accounts table uses GENERATED ALWAYS AS IDENTITY, so we must
-- use OVERRIDING SYSTEM VALUE to insert a specific id.
INSERT INTO accounts (id, email, local_only)
OVERRIDING SYSTEM VALUE
VALUES (1, 'local@open-wearable-insights.local', TRUE)
ON CONFLICT (id) DO NOTHING;
