-- API keys for authenticating clients of the /v1/* API. Raw keys are never stored: only a
-- SHA-256 hash, so a leaked database backup doesn't hand out usable credentials.
CREATE TABLE api_keys (
    id                    BIGSERIAL PRIMARY KEY,
    client_name           VARCHAR(255) NOT NULL,
    key_hash              VARCHAR(64) NOT NULL,
    rate_limit_per_minute INTEGER NOT NULL,
    daily_quota           INTEGER NOT NULL,
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_api_keys_key_hash UNIQUE (key_hash)
);

-- Every authenticated request looks up its key by hash, so this is the hot path index.
CREATE INDEX idx_api_keys_key_hash ON api_keys (key_hash);
