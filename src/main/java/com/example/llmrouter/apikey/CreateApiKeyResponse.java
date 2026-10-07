package com.example.llmrouter.apikey;

/**
 * Response body for {@code POST /admin/api-keys}. {@code apiKey} is the raw key: it is shown
 * here once and only once — it is not retrievable afterward (the server only ever stores its
 * hash). Save it now.
 */
public record CreateApiKeyResponse(
        Long id,
        String clientName,
        String apiKey,
        int rateLimitPerMinute,
        int dailyQuota) {
}
