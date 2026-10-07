package com.example.llmrouter.usage;

/**
 * Raw aggregate for one (provider, model) pair across all clients — model economics aren't
 * tenant-specific, so unlike {@link ClientUsageAggregate} this is never scoped to one API key.
 * See {@link UsageLogRepository#summarizeByModel()}.
 */
public record ModelUsageAggregate(
        String provider,
        String model,
        long totalRequests,
        double totalVirtualCost,
        double avgLatencyMs) {
}
