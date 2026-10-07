package com.example.llmrouter.provider.gemini;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for {@link GeminiProvider}, bound from {@code llm.provider.gemini.*}.
 */
@ConfigurationProperties(prefix = "llm.provider.gemini")
public class GeminiProperties {

    /** Read from the GEMINI_API_KEY env var. Blank until configured. */
    private String apiKey = "";

    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";

    /**
     * TODO: verify this is still a current free-tier model before relying on it —
     * see https://ai.google.dev/gemini-api/docs/models
     */
    private String model = "gemini-1.5-flash";

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
