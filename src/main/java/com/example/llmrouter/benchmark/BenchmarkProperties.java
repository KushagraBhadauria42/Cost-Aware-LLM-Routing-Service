package com.example.llmrouter.benchmark;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Stage 9 benchmark harness, bound from {@code llm.benchmark.*}.
 */
@ConfigurationProperties(prefix = "llm.benchmark")
public class BenchmarkProperties {

    /**
     * If {@code true}, a {@code BenchmarkRunner} ({@code CommandLineRunner}) executes the full
     * benchmark once on application startup and prints/writes a report. Defaults to {@code false}
     * so a normal {@code spring-boot:run} (or any test's Spring context) never runs it by accident.
     */
    private boolean enabled = false;

    /**
     * If {@code true}, candidates whose chosen provider is NOT "mock" are actually called over
     * the network (consuming real, possibly-paid API quota) instead of having their cost/latency
     * simulated from {@code models.yml}'s virtual numbers. Defaults to {@code false} - opt-in only,
     * so the benchmark never silently burns a real provider's free-tier limit.
     */
    private boolean useRealProviders = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isUseRealProviders() {
        return useRealProviders;
    }

    public void setUseRealProviders(boolean useRealProviders) {
        this.useRealProviders = useRealProviders;
    }
}
