-- Blood biomarker import (docs/product/parity-matrix.md row 22): user-
-- uploaded lab bloodwork results, imported via CSV. Deliberately a separate
-- table from `measurements` (continuous wearable telemetry) rather than
-- reusing it: lab readings carry their own per-reading reference range and
-- a lab-style category, and arrive as discrete, infrequent, user-supplied
-- records rather than a device data stream. Every row is attributed to a
-- provenance record for the same reason every imported record in this
-- schema is (see V01), so an import can always be traced back to the file
-- it came from.
CREATE TABLE biomarker_readings (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id      BIGINT NOT NULL REFERENCES accounts(id),
    provenance_id   BIGINT NOT NULL REFERENCES provenance(id),
    biomarker_name  TEXT NOT NULL,
    category        TEXT, -- nullable: only filled from the user's own CSV column or a known-marker lookup table, never guessed
    value           DOUBLE PRECISION NOT NULL,
    unit            TEXT NOT NULL,
    reference_low   DOUBLE PRECISION, -- nullable: only ever the user's own CSV column, never a synthesized "typical" range
    reference_high  DOUBLE PRECISION,
    reading_date    DATE NOT NULL
);

CREATE INDEX idx_biomarker_readings_account_date ON biomarker_readings (account_id, reading_date DESC);
CREATE INDEX idx_biomarker_readings_account_name_date ON biomarker_readings (account_id, biomarker_name, reading_date DESC);
