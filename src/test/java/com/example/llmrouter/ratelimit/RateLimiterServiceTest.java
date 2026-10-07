package com.example.llmrouter.ratelimit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link RateLimiterService} against a real Redis (Testcontainers). Builds the
 * {@link StringRedisTemplate} directly (no Spring context) since the only thing under test is
 * Redis counter/TTL behavior, not Spring wiring — keeps this test fast and focused.
 */
@Testcontainers
class RateLimiterServiceTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static StringRedisTemplate redisTemplate;

    @BeforeAll
    static void setUpRedisTemplate() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterEach
    void flushRedis() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    private static String uniqueClientKey() {
        return "test-client-" + UUID.randomUUID();
    }

    @Test
    void allowsRequestsUpToTheLimit_thenBlocksWithTheRateLimitType() {
        RateLimiterService rateLimiter = new RateLimiterService(redisTemplate, Clock.systemUTC());
        String clientKey = uniqueClientKey();

        for (int i = 0; i < 3; i++) {
            assertThat(rateLimiter.checkAndIncrement(clientKey, 3, 1000).allowed()).isTrue();
        }

        RateLimitResult blocked = rateLimiter.checkAndIncrement(clientKey, 3, 1000);

        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.limitType()).isEqualTo(RateLimitResult.RATE_LIMIT_PER_MINUTE);
        assertThat(blocked.retryAfterSeconds()).isGreaterThan(0);
    }

    @Test
    void reportsASensibleRetryAfter_basedOnSecondsRemainingInTheCurrentMinute() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-01-01T00:00:10Z"), ZoneOffset.UTC);
        RateLimiterService rateLimiter = new RateLimiterService(redisTemplate, fixedClock);
        String clientKey = uniqueClientKey();

        rateLimiter.checkAndIncrement(clientKey, 1, 1000); // consume the only allowed request
        RateLimitResult blocked = rateLimiter.checkAndIncrement(clientKey, 1, 1000);

        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isEqualTo(50); // 60 - 10 seconds already elapsed
    }

    @Test
    void blocksWithTheDailyQuotaType_whenRateLimitIsNotTheBottleneck() {
        RateLimiterService rateLimiter = new RateLimiterService(redisTemplate, Clock.systemUTC());
        String clientKey = uniqueClientKey();

        assertThat(rateLimiter.checkAndIncrement(clientKey, 1000, 2).allowed()).isTrue();
        assertThat(rateLimiter.checkAndIncrement(clientKey, 1000, 2).allowed()).isTrue();
        RateLimitResult blocked = rateLimiter.checkAndIncrement(clientKey, 1000, 2);

        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.limitType()).isEqualTo(RateLimitResult.DAILY_QUOTA);
        assertThat(blocked.retryAfterSeconds()).isGreaterThan(0);
    }

    @Test
    void differentClientKeys_areTrackedIndependently() {
        RateLimiterService rateLimiter = new RateLimiterService(redisTemplate, Clock.systemUTC());
        String clientA = uniqueClientKey();
        String clientB = uniqueClientKey();

        rateLimiter.checkAndIncrement(clientA, 1, 1000); // exhausts client A's budget
        RateLimitResult clientABlocked = rateLimiter.checkAndIncrement(clientA, 1, 1000);
        RateLimitResult clientBAllowed = rateLimiter.checkAndIncrement(clientB, 1, 1000);

        assertThat(clientABlocked.allowed()).isFalse();
        assertThat(clientBAllowed.allowed()).isTrue();
    }
}
