-- Phase 3: persist each day's computed readiness score.
-- Needed so /diff can compare today against a REAL prior day instead of a
-- synthetic baseline-derived stand-in (which forced every prior-day
-- deviation to exactly zero by construction).
CREATE TABLE readiness_score_history (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id             BIGINT NOT NULL REFERENCES accounts(id),
    score_date             DATE NOT NULL,
    score                  INTEGER NOT NULL,
    algorithm_version      TEXT NOT NULL,
    provisional            BOOLEAN NOT NULL,
    baseline_period        TEXT NOT NULL,
    confidence             TEXT NOT NULL,
    data_quality           TEXT NOT NULL,
    factors                JSONB NOT NULL,
    missing_data_treatment TEXT NOT NULL,
    explanation            TEXT NOT NULL,
    limitations            TEXT NOT NULL,
    computed_at            TIMESTAMPTZ NOT NULL,
    UNIQUE (account_id, score_date)
);
