-- Open Wearable Insights — TimescaleDB initialization
-- Runs once on first Postgres container start.

-- Create the TimescaleDB extension (Apache 2.0).
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- The application schema is managed by Flyway migrations in services/api.
-- This script only ensures the extension is available.