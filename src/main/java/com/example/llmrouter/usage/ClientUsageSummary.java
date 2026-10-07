package com.example.llmrouter.usage;

/**
 * Derived, caller-facing usage summary for a single client (the authenticated caller's own API
 * key — see {@code UsageController} for why this is scoped to "self" rather than exposing every
 * client's numbers to any key holder).
 *
 * @param cacheHitRatePercent  {@code cacheHits / totalRequests * 100}; {@code 0} when there have
 *                             been no requests yet
 * @param totalSavings         {@code totalBaselineCost - totalVirtualCost}
 * @param savingsPercent       {@code totalSavings / totalBaselineCost * 100}; {@code 0} when
 *                             {@code totalBaselineCost} is zero (nothing to save against)
 */
public record ClientUsageSummary(
        Long apiKeyId,
        String clientName,
        long totalRequests,
        long cacheHits,
        double cacheHitRatePercent,
        double totalVirtualCost,
        double totalBaselineCost,
        double totalSavings,
        double savingsPercent) {
}
