package com.example.llmrouter.benchmark;

/**
 * Aggregated results for one {@link BenchmarkStrategy} across every prompt in the benchmark set.
 *
 * @param strategy          the strategy these numbers describe
 * @param promptCount       how many prompts were replayed
 * @param totalVirtualCost  sum of every sample's virtual cost
 * @param totalBaselineCost sum of every sample's baseline cost (always-most-expensive-model cost)
 * @param savingsPercent    {@code (totalBaselineCost - totalVirtualCost) / totalBaselineCost * 100},
 *                          {@code 0.0} if {@code totalBaselineCost} is {@code 0.0} - same formula
 *                          {@code UsageLogService.summaryForClient} uses for real traffic
 * @param avgLatencyMs      mean latency across all samples
 * @param p50LatencyMs      median latency across all samples
 * @param p95LatencyMs      95th-percentile latency across all samples
 * @param realNetworkCalls  how many samples actually hit a real, non-mock provider over the network
 */
public record BenchmarkStrategyReport(
        BenchmarkStrategy strategy,
        int promptCount,
        double totalVirtualCost,
        double totalBaselineCost,
        double savingsPercent,
        double avgLatencyMs,
        double p50LatencyMs,
        double p95LatencyMs,
        long realNetworkCalls) {
}
