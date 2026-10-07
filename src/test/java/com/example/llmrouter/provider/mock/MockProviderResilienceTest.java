package com.example.llmrouter.provider.mock;

import com.example.llmrouter.apikey.ApiKeyRepository;
import com.example.llmrouter.usage.UsageLogRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the Resilience4j annotations on {@link MockProvider#complete(String)} are actually
 * applied: this only works if Spring creates an AOP proxy around the {@code MockProvider} bean,
 * which requires {@code spring-boot-starter-aop} on the classpath. Uses {@link MockProvider}
 * (rather than WireMock) for failure injection since it's a plain, local, configurable
 * {@code LlmProvider} with no network involved.
 * <p>
 * Datasource/JPA/Flyway autoconfiguration is excluded so this slice test starts in milliseconds
 * without needing a real Postgres (unlike {@code LlmRouterApplicationTests}, which boots the
 * full app against Testcontainers). Redis autoconfiguration is left enabled (and thus so is
 * {@code ChatCacheService}, a dependency of nothing exercised here) because Lettuce's
 * connection factory connects lazily on first use, not at bean-creation/context-startup time, so
 * no real Redis needs to be reachable just to satisfy the bean graph. {@link ApiKeyRepository}
 * and {@link UsageLogRepository} (both Spring Data JPA repositories, needed to build
 * {@code ApiKeyService}/{@code UsageLogService} -> the API-key filter's and {@code ChatService}'s
 * bean graphs) are mocked out instead, since a JPA repository proxy can't be created at all
 * without a real {@code DataSource}/{@code EntityManagerFactory}.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "llm.provider.mock.min-latency-ms=0",
        "llm.provider.mock.max-latency-ms=0",
        "llm.provider.mock.failure-rate=1.0",
        "resilience4j.retry.instances.mock.max-attempts=3",
        "resilience4j.retry.instances.mock.wait-duration=10ms",
        "resilience4j.circuitbreaker.instances.mock.sliding-window-size=4",
        "resilience4j.circuitbreaker.instances.mock.minimum-number-of-calls=2",
        "resilience4j.circuitbreaker.instances.mock.failure-rate-threshold=50",
        "resilience4j.circuitbreaker.instances.mock.wait-duration-in-open-state=200ms",
        "resilience4j.circuitbreaker.instances.mock.permitted-number-of-calls-in-half-open-state=1"
})
class MockProviderResilienceTest {

    @MockitoBean
    private ApiKeyRepository apiKeyRepository;

    @MockitoBean
    private UsageLogRepository usageLogRepository;

    @Autowired
    private MockProvider mockProvider;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Test
    void alwaysFailingProvider_eventuallyOpensTheCircuitAndFallsBackToAProviderException() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("mock");

        // Each call to complete() is retried up to 3 times by @Retry before failing, and every
        // failure (after retries are exhausted) is recorded by @CircuitBreaker. With a 100%
        // failure rate and minimum-number-of-calls=2, the circuit should open quickly.
        for (int i = 0; i < 5; i++) {
            try {
                mockProvider.complete("hello");
            } catch (Exception ignored) {
                // expected: every call fails since failure-rate=1.0
            }
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Once OPEN, Resilience4j should short-circuit immediately (no retries attempted) and
        // the fallbackMethod should normalize the result into a plain ProviderException.
        assertThatThrownBy(() -> mockProvider.complete("hello"))
                .isInstanceOf(com.example.llmrouter.provider.ProviderException.class);
    }
}
