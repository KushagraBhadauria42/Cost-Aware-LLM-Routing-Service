package com.example.llmrouter.apikey;

/**
 * Pairs a persisted {@link ApiKey} with the raw (unhashed) key that was just generated for it.
 * This is the only point in the system where the raw key ever exists — {@link ApiKeyService}
 * returns it to the caller and then discards it; it is never logged or stored.
 */
public record CreatedApiKey(ApiKey apiKey, String rawKey) {
}
