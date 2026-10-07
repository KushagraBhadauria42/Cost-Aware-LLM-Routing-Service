package com.example.llmrouter.routing;

/**
 * Coarse quality ranking used by {@link RoutingStrategy} implementations to compare models.
 * Deliberately a simple 3-tier scale rather than a numeric score — good enough to pick "the best
 * available model" without pretending to model real benchmark differences precisely.
 */
public enum QualityTier {
    LOW, MEDIUM, HIGH
}
