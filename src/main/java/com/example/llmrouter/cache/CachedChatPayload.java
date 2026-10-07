package com.example.llmrouter.cache;

/**
 * Snapshot of everything about a successful {@code ChatResponse} that is worth replaying
 * verbatim on a cache hit. Deliberately excludes {@code priorityUsed} (implied by the cache key
 * the caller looked up) and {@code cacheHit}/{@code latencyMs} (both re-derived fresh on every
 * read: a hit always reports {@code latencyMs = 0} since no provider call was made).
 * <p>
 * Serialized to/from JSON by {@link ChatCacheService} for storage in Redis, so this must stay a
 * plain, Jackson-friendly data holder (no interfaces, no polymorphism).
 */
public record CachedChatPayload(
        String responseText,
        String provider,
        String model,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        String routingReason,
        boolean fallbackUsed,
        String fallbackReason) {
}
