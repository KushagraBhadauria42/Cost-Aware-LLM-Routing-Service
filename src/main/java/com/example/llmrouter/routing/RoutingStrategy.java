package com.example.llmrouter.routing;

import java.util.List;

/**
 * Picks one {@link ModelSpec} out of the available candidates for a given request. Each
 * {@link com.example.llmrouter.chat.Priority} maps to exactly one implementation (see
 * {@link RoutingEngine}).
 */
public interface RoutingStrategy {

    /**
     * @param candidates all models currently known to the registry; never empty (callers check
     *                    that before invoking a strategy)
     */
    RoutingDecision select(RoutingContext context, List<ModelSpec> candidates);

    /**
     * Same ranking this strategy uses internally, but returns every candidate ordered
     * best-to-worst instead of just the top pick. Used by {@code ChatService} to build a
     * fallback chain: if the best choice's provider fails, the next-best candidate is tried.
     *
     * @param candidates all models currently known to the registry; never empty (callers check
     *                    that before invoking a strategy)
     */
    List<ModelSpec> rank(RoutingContext context, List<ModelSpec> candidates);
}
