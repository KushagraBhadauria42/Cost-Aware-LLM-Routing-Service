package com.example.llmrouter.apikey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A client's credential for the {@code /v1/*} API. Only {@link #keyHash} (a SHA-256 hex digest)
 * is ever persisted — the raw key is generated once in {@link ApiKeyService#createApiKey} and
 * returned to the caller exactly once; it cannot be recovered afterward, only revoked
 * (set {@code active = false}) and replaced with a new key.
 */
@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_name", nullable = false)
    private String clientName;

    @Column(name = "key_hash", nullable = false, unique = true)
    private String keyHash;

    @Column(name = "rate_limit_per_minute", nullable = false)
    private int rateLimitPerMinute;

    @Column(name = "daily_quota", nullable = false)
    private int dailyQuota;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ApiKey() {
        // required by JPA
    }

    public ApiKey(String clientName, String keyHash, int rateLimitPerMinute, int dailyQuota) {
        this.clientName = clientName;
        this.keyHash = keyHash;
        this.rateLimitPerMinute = rateLimitPerMinute;
        this.dailyQuota = dailyQuota;
        this.active = true;
        this.createdAt = Instant.now();
    }

    public Long id() {
        return id;
    }

    public String clientName() {
        return clientName;
    }

    public String keyHash() {
        return keyHash;
    }

    public int rateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public int dailyQuota() {
        return dailyQuota;
    }

    public boolean active() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
