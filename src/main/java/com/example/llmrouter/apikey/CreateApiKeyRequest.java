package com.example.llmrouter.apikey;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Request body for {@code POST /admin/api-keys}.
 *
 * @param clientName          human-readable label for who/what this key belongs to; required
 * @param rateLimitPerMinute  max requests this key may make per rolling minute; defaults to 60
 * @param dailyQuota          max requests this key may make per UTC calendar day; defaults to 1000
 */
public record CreateApiKeyRequest(

        @NotBlank(message = "clientName must not be blank")
        String clientName,

        @Positive(message = "rateLimitPerMinute must be positive")
        Integer rateLimitPerMinute,

        @Positive(message = "dailyQuota must be positive")
        Integer dailyQuota) {

    private static final int DEFAULT_RATE_LIMIT_PER_MINUTE = 60;
    private static final int DEFAULT_DAILY_QUOTA = 1000;

    public CreateApiKeyRequest {
        if (rateLimitPerMinute == null) {
            rateLimitPerMinute = DEFAULT_RATE_LIMIT_PER_MINUTE;
        }
        if (dailyQuota == null) {
            dailyQuota = DEFAULT_DAILY_QUOTA;
        }
    }
}
