package com.example.llmrouter.ratelimit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Enforces a per-client requests-per-minute rate limit and a per-client requests-per-UTC-day
 * quota, both backed by Redis counters with a TTL so they self-clean (no cron job needed to
 * reset counts). Every authenticated request increments both counters exactly once — including
 * requests later served from the Stage 5 response cache — because these limits protect the HTTP
 * API surface and enforce per-client fairness, not provider spend (see
 * {@code ApiKeyAuthFilter} and the Stage 6 teaching summary for the full rationale).
 * <p>
 * Each window uses a simple fixed-bucket counter (key = client + current minute, or client +
 * current UTC date): {@code INCR}, and if this was the first increment in that bucket,
 * {@code EXPIRE} it. This is intentionally a check-and-increment done as a single atomic
 * {@code INCR}, not a separate read-then-write — so concurrent requests from the same client
 * can't race past the limit the way a naive "GET count, compare, SET count+1" would.
 */
@Service
public class RateLimiterService {

    private static final Duration PER_MINUTE_WINDOW_TTL = Duration.ofSeconds(120);
    private static final Duration DAILY_WINDOW_TTL_BUFFER = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    @Autowired
    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this(redisTemplate, Clock.systemUTC());
    }

    RateLimiterService(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    /**
     * Checks and increments both windows for {@code clientKey} (typically an API key's database
     * id, as a string). The per-minute limit is checked first: if it's already exceeded, the
     * daily quota counter is left untouched for this request (it was already incremented by
     * whichever earlier request tipped the per-minute bucket over).
     */
    public RateLimitResult checkAndIncrement(String clientKey, int rateLimitPerMinute, int dailyQuota) {
        Instant now = clock.instant();

        long currentMinuteBucket = now.getEpochSecond() / 60;
        long secondsUntilNextMinute = Math.max(1, 60 - (now.getEpochSecond() % 60));
        RateLimitResult perMinuteResult = checkWindow(
                "ratelimit:minute:" + clientKey + ":" + currentMinuteBucket,
                rateLimitPerMinute,
                PER_MINUTE_WINDOW_TTL,
                RateLimitResult.RATE_LIMIT_PER_MINUTE,
                secondsUntilNextMinute);
        if (!perMinuteResult.allowed()) {
            return perMinuteResult;
        }

        ZonedDateTime utcNow = now.atZone(ZoneOffset.UTC);
        String utcDate = utcNow.toLocalDate().toString();
        Instant nextUtcMidnight = utcNow.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        long secondsUntilMidnight = Math.max(1, Duration.between(now, nextUtcMidnight).getSeconds());
        return checkWindow(
                "ratelimit:day:" + clientKey + ":" + utcDate,
                dailyQuota,
                Duration.ofSeconds(secondsUntilMidnight).plus(DAILY_WINDOW_TTL_BUFFER),
                RateLimitResult.DAILY_QUOTA,
                secondsUntilMidnight);
    }

    private RateLimitResult checkWindow(String key, int limit, Duration ttl, String limitType, long retryAfterSeconds) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, ttl);
        }
        if (count != null && count > limit) {
            return RateLimitResult.blocked(limitType, retryAfterSeconds);
        }
        return RateLimitResult.allow();
    }
}
