-- Open Wearable Insights — initial schema
-- UTC storage; source time zone preserved; provenance on every record.

-- TimescaleDB extension (should already exist from init-timescaledb.sql)
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- ---------------------------------------------------------------------------
-- Accounts & devices
-- ---------------------------------------------------------------------------
CREATE TABLE accounts (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email        TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    local_only   BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE devices (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id    BIGINT NOT NULL REFERENCES accounts(id),
    manufacturer  TEXT,
    model         TEXT,
    serial_hash   TEXT,
    tz            TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Import batches & raw payloads
-- ---------------------------------------------------------------------------
CREATE TABLE import_batches (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id    BIGINT NOT NULL REFERENCES accounts(id),
    source        TEXT NOT NULL, -- fit/csv/json/synthetic/manual/vendor_api
    content_hash  TEXT NOT NULL,
    status        TEXT NOT NULL DEFAULT 'imported',
    imported_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    undo_at       TIMESTAMPTZ
);

CREATE TABLE raw_payloads (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_id      BIGINT NOT NULL REFERENCES import_batches(id),
    payload       JSONB NOT NULL,
    checksum      TEXT NOT NULL,
    received_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Provenance
-- ---------------------------------------------------------------------------
CREATE TABLE provenance (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source           TEXT NOT NULL,
    import_batch_id  BIGINT REFERENCES import_batches(id),
    algorithm_version TEXT,
    recorded_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Algorithm versions
-- ---------------------------------------------------------------------------
CREATE TABLE algorithm_versions (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         TEXT NOT NULL,
    version      TEXT NOT NULL,
    description  TEXT,
    released_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (name, version)
);

-- ---------------------------------------------------------------------------
-- Measurements (hypertable — time-series)
-- ---------------------------------------------------------------------------
CREATE TABLE measurements (
    time          TIMESTAMPTZ NOT NULL,
    source_tz     TEXT,
    account_id    BIGINT NOT NULL REFERENCES accounts(id),
    device_id     BIGINT REFERENCES devices(id),
    metric_type   TEXT NOT NULL, -- hr/hrv/rhr/sleep_stage/steps/calories/stress/spo2/respiration/...
    value         DOUBLE PRECISION NOT NULL,
    unit          TEXT NOT NULL,
    source        TEXT NOT NULL, -- raw/vendor/app
    provenance_id BIGINT NOT NULL REFERENCES provenance(id),
    quality_flag  TEXT
);

-- Convert measurements to a TimescaleDB hypertable
SELECT create_hypertable('measurements', 'time');

-- Index for common queries
CREATE INDEX idx_measurements_account_time ON measurements (account_id, time DESC);
CREATE INDEX idx_measurements_metric ON measurements (metric_type, time DESC);

-- ---------------------------------------------------------------------------
-- Derived metrics (versioned)
-- ---------------------------------------------------------------------------
CREATE TABLE derived_metrics (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id        BIGINT NOT NULL REFERENCES accounts(id),
    metric            TEXT NOT NULL, -- readiness/sleep_summary/training_load/...
    value             JSONB NOT NULL,
    algorithm_version TEXT NOT NULL,
    computed_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    baseline_period   TEXT,
    confidence        TEXT,
    data_quality      TEXT,
    inputs            JSONB,
    explanation       TEXT
);

CREATE INDEX idx_derived_account_metric ON derived_metrics (account_id, metric, computed_at DESC);

-- ---------------------------------------------------------------------------
-- Journal entries (untrusted input)
-- ---------------------------------------------------------------------------
CREATE TABLE journal_entries (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id            BIGINT NOT NULL REFERENCES accounts(id),
    time                  TIMESTAMPTZ NOT NULL,
    behavior              TEXT NOT NULL,
    value                 TEXT,
    note                  TEXT,
    treated_as_untrusted  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- LLM outputs
-- ---------------------------------------------------------------------------
CREATE TABLE llm_outputs (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id     BIGINT NOT NULL REFERENCES accounts(id),
    prompt_hash    TEXT,
    output         JSONB NOT NULL,
    schema_valid   BOOLEAN NOT NULL,
    fallback_used  BOOLEAN NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);