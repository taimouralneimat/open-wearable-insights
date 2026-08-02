-- Garmin Connect SSO connector: persists the account's connection status and
-- OAuth tokens (never the raw password — see GarminConnectAuthClient).
-- Single-user local app: one row per account, upserted on every state change.
CREATE TABLE garmin_connect_account (
    account_id       BIGINT PRIMARY KEY REFERENCES accounts(id),
    status           TEXT NOT NULL DEFAULT 'disconnected', -- disconnected/mfa_required/connected/error
    email            TEXT,
    di_token         TEXT,
    di_refresh_token TEXT,
    di_client_id     TEXT,
    last_error       TEXT,
    connected_at     TIMESTAMPTZ,
    last_sync_at     TIMESTAMPTZ,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
