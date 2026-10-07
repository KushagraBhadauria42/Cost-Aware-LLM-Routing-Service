package com.example.llmrouter.provider.mock;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for {@link MockProvider}, bound from {@code llm.provider.mock.*}.
 * Lets us simulate a flaky, slow provider in tests and demos without calling anything real.
 */
@ConfigurationProperties(prefix = "llm.provider.mock")
public class MockProviderProperties {

    /** Lower bound of the simulated network/inference delay, in milliseconds. */
    private long minLatencyMs = 50;

    /** Upper bound of the simulated network/inference delay, in milliseconds. */
    private long maxLatencyMs = 300;

    /** Probability (0.0-1.0) that a given call throws a {@code ProviderException}. */
    private double failureRate = 0.0;

    /**
     * If the simulated latency for a call would exceed this value, a "simulated timeout"
     * {@code ProviderException} is thrown immediately instead of actually sleeping — keeps
     * timeout-injection tests fast and deterministic. {@code 0} (default) disables timeout
     * simulation entirely.
     */
    private long timeoutMs = 0;

    /** Fake model name reported back in responses. */
    private String model = "mock-model-v1";

    public long getMinLatencyMs() {
        return minLatencyMs;
    }

    public void setMinLatencyMs(long minLatencyMs) {
        this.minLatencyMs = minLatencyMs;
    }

    public long getMaxLatencyMs() {
        return maxLatencyMs;
    }

    public void setMaxLatencyMs(long maxLatencyMs) {
        this.maxLatencyMs = maxLatencyMs;
    }

    public double getFailureRate() {
        return failureRate;
    }

    public void setFailureRate(double failureRate) {
        this.failureRate = failureRate;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
