package com.example.llmrouter.usage;

import com.example.llmrouter.chat.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row per {@code /v1/chat} request that produced a response (successful first-try, fallback,
 * or cache hit). Rows are never written for a request where every candidate provider failed —
 * there is no cost, model, or latency to attribute in that case.
 * <p>
 * Cache hits get their own row too (with {@code virtualCost = 0} and {@code cacheHit = true}),
 * rather than being skipped: without a row, {@code /v1/usage} could not report an accurate
 * cache-hit rate or compare "requests served" against "requests that cost something" — exactly
 * the kind of number this stage exists to produce (see the Stage 7 teaching summary for the full
 * rationale).
 */
@Entity
@Table(name = "usage_log")
public class UsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_key_id", nullable = false)
    private Long apiKeyId;

    @Column(name = "client_name", nullable = false)
    private String clientName;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private Priority priority;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private int completionTokens;

    @Column(name = "total_tokens", nullable = false)
    private int totalTokens;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "virtual_cost", nullable = false)
    private double virtualCost;

    @Column(name = "baseline_cost", nullable = false)
    private double baselineCost;

    @Column(name = "cache_hit", nullable = false)
    private boolean cacheHit;

    @Column(name = "fallback_used", nullable = false)
    private boolean fallbackUsed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected UsageLog() {
        // required by JPA
    }

    public UsageLog(Long apiKeyId, String clientName, Priority priority, String provider, String model,
            int promptTokens, int completionTokens, int totalTokens, long latencyMs,
            double virtualCost, double baselineCost, boolean cacheHit, boolean fallbackUsed) {
        this.apiKeyId = apiKeyId;
        this.clientName = clientName;
        this.priority = priority;
        this.provider = provider;
        this.model = model;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.latencyMs = latencyMs;
        this.virtualCost = virtualCost;
        this.baselineCost = baselineCost;
        this.cacheHit = cacheHit;
        this.fallbackUsed = fallbackUsed;
    }

    public Long id() {
        return id;
    }

    public Long apiKeyId() {
        return apiKeyId;
    }

    public String clientName() {
        return clientName;
    }

    public Priority priority() {
        return priority;
    }

    public String provider() {
        return provider;
    }

    public String model() {
        return model;
    }

    public int promptTokens() {
        return promptTokens;
    }

    public int completionTokens() {
        return completionTokens;
    }

    public int totalTokens() {
        return totalTokens;
    }

    public long latencyMs() {
        return latencyMs;
    }

    public double virtualCost() {
        return virtualCost;
    }

    public double baselineCost() {
        return baselineCost;
    }

    public boolean cacheHit() {
        return cacheHit;
    }

    public boolean fallbackUsed() {
        return fallbackUsed;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
