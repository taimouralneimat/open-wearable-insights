-- Phase 4 (ADR-0006 follow-up): discrete workout/activity sessions, distinct
-- from the continuous point-in-time telemetry in `measurements`. Parsed
-- from FIT SessionMesg via GarminFitConnector.parseActivities().
CREATE TABLE activities (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id       BIGINT NOT NULL REFERENCES accounts(id),
    provenance_id    BIGINT NOT NULL REFERENCES provenance(id),
    sport            TEXT NOT NULL,
    start_time       TIMESTAMPTZ NOT NULL,
    end_time         TIMESTAMPTZ NOT NULL,
    duration_seconds DOUBLE PRECISION NOT NULL,
    distance_meters  DOUBLE PRECISION,
    avg_speed_mps    DOUBLE PRECISION,
    max_speed_mps    DOUBLE PRECISION,
    avg_heart_rate   INTEGER,
    max_heart_rate   INTEGER,
    calories         INTEGER,
    hr_zone_seconds  JSONB
);

CREATE INDEX idx_activities_account_time ON activities (account_id, start_time DESC);
