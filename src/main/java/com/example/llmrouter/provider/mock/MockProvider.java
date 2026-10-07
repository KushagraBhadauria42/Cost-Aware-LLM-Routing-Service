package com.example.llmrouter.provider.mock;

import com.example.llmrouter.provider.LlmProvider;
import com.example.llmrouter.provider.ProviderException;
import com.example.llmrouter.provider.ProviderResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Deterministic-enough, fully local stand-in for a real LLM provider. Used so the rest of the
 * service (routing, caching, resilience, metrics) can be built and tested for free, without
 * depending on any provider's uptime or rate limits.
 * <p>
 * Latency, failure rate and a simulated timeout threshold are all configurable via
 * {@link MockProviderProperties}, which makes this bean the easiest way to exercise the
 * Resilience4j retry/circuit-breaker wiring (instance name "mock") and the {@code ChatService}
 * fallback chain in tests, without needing WireMock or real network calls.
 * <p>
 * No longer {@code @Primary} as of Stage 3: {@code ChatService} now injects all
 * {@link LlmProvider} beans (keyed by {@link LlmProvider#name()}) and lets the
 * {@link com.example.llmrouter.routing.RoutingEngine} pick one explicitly, so there's no more
 * single-provider injection point to disambiguate.
 */
@Component
public class MockProvider implements LlmProvider {

    private static final String PROVIDER_NAME = "mock";

    private final MockProviderProperties properties;

    public MockProvider(MockProviderProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    @Retry(name = PROVIDER_NAME)
    @CircuitBreaker(name = PROVIDER_NAME, fallbackMethod = "completeFallback")
    public ProviderResult complete(String prompt) {
        long startNanos = System.nanoTime();
        simulateLatency();
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;

        maybeFail();

        int promptTokens = estimateTokens(prompt);
        int completionTokens = Math.max(1, promptTokens / 2);
        String text = "Mock response to: " + truncate(prompt);

        return new ProviderResult(text, properties.getModel(), promptTokens, completionTokens, latencyMs);
    }

    /**
     * Invoked by the Resilience4j circuit breaker aspect whenever {@link #complete(String)}
     * ultimately fails (retries exhausted, or the circuit is open so the call was never
     * attempted). Normalizes every failure mode into a {@link ProviderException} so callers only
     * ever need to handle one exception type.
     */
    private ProviderResult completeFallback(String prompt, Throwable t) {
        if (t instanceof ProviderException providerException) {
            throw providerException;
        }
        throw new ProviderException(PROVIDER_NAME, "Resilience fallback triggered: " + t.getMessage(), t);
    }

    private void simulateLatency() {
        long min = properties.getMinLatencyMs();
        long max = Math.max(min, properties.getMaxLatencyMs());
        long delay = (min == max) ? min : ThreadLocalRandom.current().nextLong(min, max + 1);
        if (delay <= 0) {
            return;
        }

        long timeoutMs = properties.getTimeoutMs();
        if (timeoutMs > 0 && delay > timeoutMs) {
            // Simulate a timeout without actually sleeping the full (possibly huge) delay, so
            // timeout-injection tests stay fast and deterministic.
            throw new ProviderException(
                    PROVIDER_NAME,
                    "Simulated timeout: estimated latency " + delay + "ms exceeds configured timeoutMs=" + timeoutMs);
        }

        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException(PROVIDER_NAME, "Interrupted while simulating latency", e);
        }
    }

    private void maybeFail() {
        if (ThreadLocalRandom.current().nextDouble() < properties.getFailureRate()) {
            throw new ProviderException(
                    PROVIDER_NAME,
                    "Simulated failure (configured failureRate=" + properties.getFailureRate() + ")");
        }
    }

    private int estimateTokens(String text) {
        return Math.max(1, text.trim().split("\\s+").length);
    }

    private String truncate(String text) {
        return text.length() <= 60 ? text : text.substring(0, 60) + "...";
    }
}
