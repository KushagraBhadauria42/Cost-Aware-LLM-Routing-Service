package com.example.llmrouter.usage;

/**
 * Raw aggregate for one client (API key), summed/counted straight out of the database — see
 * {@link UsageLogRepository#summarizeByClient(Long)}. {@link UsageLogService} turns this into the
 * richer, derived {@link ClientUsageSummary} (hit rate, savings, savings percent).
 *
 * @param apiKeyId         the API key this usage belongs to
 * @param clientName       denormalized at write time so this reads correctly even if the
 *                         underlying {@code api_keys} row's name later changes
 * @param totalRequests    every logged request (cache hits, fallbacks, and first-try successes)
 * @param cacheHits        how many of {@code totalRequests} were served from cache
 * @param totalVirtualCost sum of what was actually "spent" (zero for cache hits)
 * @param totalBaselineCost sum of what every request would have cost using the most expensive
 *                          configured model
 */
public record ClientUsageAggregate(
        Long apiKeyId,
        String clientName,
        long totalRequests,
        long cacheHits,
        double totalVirtualCost,
        double totalBaselineCost) {
}
