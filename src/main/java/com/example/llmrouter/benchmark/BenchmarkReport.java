package com.example.llmrouter.benchmark;

import java.util.List;

/**
 * The full result of one benchmark run: one {@link BenchmarkStrategyReport} per strategy that was
 * replayed, plus metadata about how the run was configured.
 */
public record BenchmarkReport(
        int promptCount,
        boolean usedRealProviders,
        List<BenchmarkStrategyReport> strategyReports) {
}
