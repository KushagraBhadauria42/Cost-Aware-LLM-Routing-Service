package com.example.llmrouter.routing;

/** Everything a {@link RoutingStrategy} needs to know about the incoming request to decide. */
public record RoutingContext(String prompt) {
}
