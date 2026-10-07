package com.example.llmrouter.provider.mock;

import com.example.llmrouter.provider.ProviderException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain unit tests (no Spring context) for {@link MockProvider}'s own behavior, including the
 * simulated-timeout feature used to inject "slow provider" scenarios in tests without actually
 * sleeping. Resilience4j's retry/circuit-breaker/fallback wiring on top of this is covered
 * separately in {@link MockProviderResilienceTest}, since that requires a Spring-managed AOP
 * proxy to actually engage.
 */
class MockProviderTimeoutTest {

    @Test
    void throwsSimulatedTimeout_whenEstimatedLatencyExceedsConfiguredTimeout() {
        MockProviderProperties properties = new MockProviderProperties();
        properties.setMinLatencyMs(500);
        properties.setMaxLatencyMs(500);
        properties.setTimeoutMs(100);

        MockProvider provider = new MockProvider(properties);

        assertThatThrownBy(() -> provider.complete("hello"))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("Simulated timeout");
    }

    @Test
    void succeedsNormally_whenTimeoutIsDisabledOrNotExceeded() {
        MockProviderProperties properties = new MockProviderProperties();
        properties.setMinLatencyMs(0);
        properties.setMaxLatencyMs(0);
        properties.setTimeoutMs(0); // disabled

        MockProvider provider = new MockProvider(properties);

        assertThat(provider.complete("hello").text()).contains("hello");
    }
}
