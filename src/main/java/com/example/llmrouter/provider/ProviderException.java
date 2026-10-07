package com.example.llmrouter.provider;

/**
 * Thrown when an {@link LlmProvider} fails to produce a completion (simulated failure,
 * network error, non-2xx response, timeout, etc.). Callers decide how to react: Stage 1
 * simply surfaces it as an HTTP 502; Stage 4 will add retries/fallback around it.
 */
public class ProviderException extends RuntimeException {

    private final String providerName;

    public ProviderException(String providerName, String message) {
        super(message);
        this.providerName = providerName;
    }

    public ProviderException(String providerName, String message, Throwable cause) {
        super(message, cause);
        this.providerName = providerName;
    }

    public String providerName() {
        return providerName;
    }
}
