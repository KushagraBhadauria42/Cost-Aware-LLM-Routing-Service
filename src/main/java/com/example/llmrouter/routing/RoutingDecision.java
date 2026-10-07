package com.example.llmrouter.routing;

/**
 * The outcome of a routing decision: which model was chosen, and a human-readable explanation of
 * why — surfaced back to the API caller so routing behavior is never a black box.
 */
public record RoutingDecision(ModelSpec chosen, String reason) {
}
