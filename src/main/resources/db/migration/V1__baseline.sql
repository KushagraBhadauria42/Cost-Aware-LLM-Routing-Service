-- Baseline migration: proves Flyway can connect to Postgres and apply a versioned schema.
-- Real feature tables (usage logs, api keys, etc.) will be added in later stages.
CREATE TABLE IF NOT EXISTS schema_baseline (
    id          BIGSERIAL PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
