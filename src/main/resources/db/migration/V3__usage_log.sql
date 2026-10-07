CREATE TABLE usage_log (
    id BIGSERIAL PRIMARY KEY,
    api_key_id BIGINT NOT NULL REFERENCES api_keys (id),
    client_name VARCHAR(255) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    model VARCHAR(255) NOT NULL,
    prompt_tokens INTEGER NOT NULL,
    completion_tokens INTEGER NOT NULL,
    total_tokens INTEGER NOT NULL,
    latency_ms BIGINT NOT NULL,
    virtual_cost DOUBLE PRECISION NOT NULL,
    baseline_cost DOUBLE PRECISION NOT NULL,
    cache_hit BOOLEAN NOT NULL,
    fallback_used BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_usage_log_api_key_id ON usage_log (api_key_id);
CREATE INDEX idx_usage_log_provider_model ON usage_log (provider, model);
CREATE INDEX idx_usage_log_created_at ON usage_log (created_at);
