-- Strength training tracking (docs/product/parity-matrix.md row 25):
-- manual set/rep/weight logging, plus an optional weekly strength-minutes
-- goal on the account.
--
-- Deliberately a new pair of tables rather than reusing `activities`:
-- `activities` is session-level (one row per workout, sport as free text,
-- continuous cardio metrics like avg_speed_mps/hr_zone_seconds that don't
-- apply to a strength session) and is populated only from parsed FIT
-- SessionMesg data (see V05). Manually-logged strength workouts need
-- rep-level detail (exercise, set order, reps, weight) that would force
-- ugly nullable columns onto every cardio row if bolted on there instead.
-- The "Strength Activity Time" trend (StrengthTrainingService) reads BOTH
-- this table and `activities` (matched by sport = 'training', the closest
-- available Garmin FIT signal — see that service's Javadoc for why this is
-- a heuristic, not exact) and reports each source's contribution honestly
-- rather than blending them into one unlabeled number.
CREATE TABLE strength_workouts (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id       BIGINT NOT NULL REFERENCES accounts(id),
    started_at       TIMESTAMPTZ NOT NULL,
    -- User-reported actual duration. Nullable: when absent,
    -- StrengthTrainingService estimates one from the set count instead of
    -- leaving the workout out of the time trend entirely — see that
    -- class's ESTIMATED_MINUTES_PER_SET javadoc for the exact, openly
    -- documented (not physiological) formula.
    duration_minutes INTEGER,
    note             TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE strength_sets (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workout_id    BIGINT NOT NULL REFERENCES strength_workouts(id) ON DELETE CASCADE,
    -- Free text, same "curated suggestions, not an enforced taxonomy"
    -- convention as journal_entries.behavior / BiomarkerCsvParser's
    -- biomarker_name — no canonical exercise-name table.
    exercise_name TEXT NOT NULL,
    set_order     INTEGER NOT NULL,
    reps          INTEGER NOT NULL,
    weight_kg     DOUBLE PRECISION, -- nullable: bodyweight exercises have no external load
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_strength_workouts_account_started ON strength_workouts (account_id, started_at DESC);
CREATE INDEX idx_strength_sets_workout ON strength_sets (workout_id);

-- Optional goal-setting (row 25's "goal-setting" acceptance criterion).
-- A dedicated nullable column, not a new SUGGESTED_GOALS entry: the
-- existing "Strength & training load" primary-goal option (ProfileService)
-- is a general identity/coaching-emphasis choice already wired into
-- journal identity votes; this is a specific numeric weekly target the
-- strength trend view can show progress against, which that free-text
-- goal list has no field for. Keeping it a single column avoids a new
-- table for one optional integer.
ALTER TABLE accounts ADD COLUMN weekly_strength_minutes_goal INTEGER;
