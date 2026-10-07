package com.example.llmrouter.usage;

/** Derived, global per-model usage summary (not scoped to any one client). */
public record ModelUsageSummary(
        String provider,
        String model,
        long totalRequests,
        double totalVirtualCost,
        double avgLatencyMs) {
}
