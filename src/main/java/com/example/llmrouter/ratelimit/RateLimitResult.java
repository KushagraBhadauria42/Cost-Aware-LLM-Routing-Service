package com.example.llmrouter.ratelimit;

/**
 * Outcome of a rate limit / quota check.
 *
 * @param allowed          true if the request may proceed
 * @param limitType        which limit was hit ({@code "RATE_LIMIT_PER_MINUTE"} or
 *                         {@code "DAILY_QUOTA"}); {@code null} when {@code allowed} is true
 * @param retryAfterSeconds how long the client should wait before retrying; {@code 0} when
 *                          {@code allowed} is true
 */
public record RateLimitResult(boolean allowed, String limitType, long retryAfterSeconds) {

    public static final String RATE_LIMIT_PER_MINUTE = "RATE_LIMIT_PER_MINUTE";
    public static final String DAILY_QUOTA = "DAILY_QUOTA";

    public static RateLimitResult allow() {
        return new RateLimitResult(true, null, 0);
    }

    public static RateLimitResult blocked(String limitType, long retryAfterSeconds) {
        return new RateLimitResult(false, limitType, retryAfterSeconds);
    }
}
