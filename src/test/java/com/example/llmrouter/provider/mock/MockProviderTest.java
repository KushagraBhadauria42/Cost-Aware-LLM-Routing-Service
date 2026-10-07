package com.example.llmrouter.provider.mock;

import com.example.llmrouter.provider.ProviderException;
import com.example.llmrouter.provider.ProviderResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockProviderTest {

    @Test
    void returnsResultWithExpectedModelAndTokenCounts_whenFailureRateIsZero() {
        MockProviderProperties properties = new MockProviderProperties();
        properties.setMinLatencyMs(0);
        properties.setMaxLatencyMs(0);
        properties.setFailureRate(0.0);
        properties.setModel("mock-model-v1");
        MockProvider provider = new MockProvider(properties);

        ProviderResult result = provider.complete("hello world this is a test prompt");

        assertThat(result.model()).isEqualTo("mock-model-v1");
        assertThat(result.promptTokens()).isEqualTo(7);
        assertThat(result.completionTokens()).isGreaterThan(0);
        assertThat(result.totalTokens()).isEqualTo(result.promptTokens() + result.completionTokens());
        assertThat(result.text()).contains("hello world this is a test prompt");
    }

    @Test
    void alwaysThrowsProviderException_whenFailureRateIsOne() {
        MockProviderProperties properties = new MockProviderProperties();
        properties.setMinLatencyMs(0);
        properties.setMaxLatencyMs(0);
        properties.setFailureRate(1.0);
        MockProvider provider = new MockProvider(properties);

        assertThatThrownBy(() -> provider.complete("anything"))
                .isInstanceOf(ProviderException.class)
                .satisfies(ex -> assertThat(((ProviderException) ex).providerName()).isEqualTo("mock"));
    }

    @Test
    void reportsLatencyAtLeastTheConfiguredMinimum() {
        MockProviderProperties properties = new MockProviderProperties();
        properties.setMinLatencyMs(50);
        properties.setMaxLatencyMs(50);
        properties.setFailureRate(0.0);
        MockProvider provider = new MockProvider(properties);

        ProviderResult result = provider.complete("timing check");

        assertThat(result.latencyMs()).isGreaterThanOrEqualTo(50);
    }

    @Test
    void name_isMock() {
        MockProvider provider = new MockProvider(new MockProviderProperties());

        assertThat(provider.name()).isEqualTo("mock");
    }
}
