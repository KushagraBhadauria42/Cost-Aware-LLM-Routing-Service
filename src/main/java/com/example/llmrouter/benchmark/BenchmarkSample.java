package com.example.llmrouter.benchmark;

/**
 * The raw outcome of replaying one prompt under one {@link BenchmarkStrategy}.
 *
 * @param strategy           which strategy produced this sample
 * @param provider           the provider the routing decision chose (or forced, for {@code ALWAYS_EXPENSIVE})
 * @param model              the model the routing decision chose
 * @param totalTokens         token count used for the cost calculation (estimated by the local, free
 *                           {@code MockProvider}, regardless of which provider was "chosen" - see
 *                           {@code BenchmarkService}'s class javadoc for why that's a deliberate choice)
 * @param latencyMs          the latency attributed to this sample, in milliseconds
 * @param usedRealNetworkCall {@code true} only when an actual non-mock provider was really called
 *                           over the network (i.e. {@code useRealProviders=true} and the call
 *                           succeeded); {@code false} when the chosen candidate was mock, or when
 *                           latency/tokens were simulated from {@code models.yml}'s virtual numbers
 * @param virtualCost        tokens * the chosen model's virtual price-per-1K-tokens
 * @param baselineCost       tokens * the globally most-expensive configured model's price-per-1K-tokens
 */
public record BenchmarkSample(
        BenchmarkStrategy strategy,
        String provider,
        String model,
        int totalTokens,
        long latencyMs,
        boolean usedRealNetworkCall,
        double virtualCost,
        double baselineCost) {
}
