package com.example.llmrouter.benchmark;

/**
 * Which routing approach a benchmark run replays the fixed prompt set under. {@link #CHEAP},
 * {@link #FAST}, {@link #QUALITY} and {@link #AUTO} map 1:1 onto {@link com.example.llmrouter.chat.Priority}
 * and go through the real {@link com.example.llmrouter.routing.RoutingEngine}. {@link #ALWAYS_EXPENSIVE}
 * is a synthetic baseline with no {@code Priority} equivalent: it always "chooses" the single
 * globally most-expensive configured model regardless of prompt content, representing what cost
 * would look like with no routing intelligence at all - the same baseline concept already used by
 * {@code UsageLogService}/{@code /v1/usage}'s "savings" calculation.
 */
public enum BenchmarkStrategy {
    ALWAYS_EXPENSIVE,
    CHEAP,
    FAST,
    QUALITY,
    AUTO
}
