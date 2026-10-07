package com.example.llmrouter.provider.groq;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for {@link GroqProvider}, bound from {@code llm.provider.groq.*}.
 * All values are overridable via env vars; the API key must never be committed.
 */
@ConfigurationProperties(prefix = "llm.provider.groq")
public class GroqProperties {

    /** Read from the GROQ_API_KEY env var. Blank until configured — provider will fail loudly if used without one. */
    private String apiKey = "";

    /** Groq exposes an OpenAI-compatible API under this base path. */
    private String baseUrl = "https://api.groq.com/openai/v1";

    /**
     * TODO: verify this is still a current free-tier model before relying on it —
     * see https://console.groq.com/docs/models
     */
    private String model = "llama-3.1-8b-instant";

    private long timeoutMs = 10_000;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
