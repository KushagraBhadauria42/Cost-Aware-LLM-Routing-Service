package com.example.llmrouter.usage;

/**
 * The one place the "tokens * virtual price-per-1K-tokens" cost formula is implemented. Shared by
 * {@link UsageLogService} (real {@code /v1/chat} traffic) and the Stage 9 benchmark harness
 * ({@code com.example.llmrouter.benchmark}) so both always agree on what a given token count costs
 * at a given price - the same "single source of truth" reasoning Stage 8 used for Micrometer
 * metrics (avoid two independent implementations of the same formula silently diverging).
 */
public final class CostCalculator {

    private CostCalculator() {
    }

    public static double cost(int totalTokens, double pricePerKTokens) {
        return (totalTokens / 1000.0) * pricePerKTokens;
    }
}
