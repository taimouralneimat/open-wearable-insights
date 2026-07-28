-- Phase 2: add file_name and record_count to import_batches
-- These columns support the import progress reporting feature.
ALTER TABLE import_batches ADD COLUMN IF NOT EXISTS file_name TEXT;
ALTER TABLE import_batches ADD COLUMN IF NOT EXISTS record_count INTEGER NOT NULL DEFAULT 0;
