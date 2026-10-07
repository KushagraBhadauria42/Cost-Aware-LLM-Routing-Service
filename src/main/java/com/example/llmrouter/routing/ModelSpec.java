package com.example.llmrouter.routing;

/**
 * One entry from {@code models.yml}: a (provider, model) pair plus the virtual metadata routing
 * strategies compare on. Prices and latencies are intentionally "virtual" — made-up planning
 * numbers for this project, not real provider billing or SLA data.
 */
public record ModelSpec(
        String provider,
        String model,
        double pricePerKTokens,
        long avgLatencyMs,
        QualityTier qualityTier) {
}
