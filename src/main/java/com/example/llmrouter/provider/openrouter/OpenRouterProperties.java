package com.example.llmrouter.provider.openrouter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for {@link OpenRouterProvider}, bound from {@code llm.provider.openrouter.*}.
 */
@ConfigurationProperties(prefix = "llm.provider.openrouter")
public class OpenRouterProperties {

    /** Read from the OPENROUTER_API_KEY env var. Blank until configured. */
    private String apiKey = "";

    private String baseUrl = "https://openrouter.ai/api/v1";

    /**
     * TODO: verify this is still a current free model before relying on it — OpenRouter's free
     * models carry a ":free" suffix and the list changes over time, see
     * https://openrouter.ai/models?max_price=0
     */
    private String model = "meta-llama/llama-3.1-8b-instruct:free";

    private long timeoutMs = 10_000;

    /**
     * Optional. OpenRouter's docs recommend (not require) an HTTP-Referer header identifying
     * the calling app for their leaderboard; left blank by default.
     */
    private String referer = "";

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

    public String getReferer() {
        return referer;
    }

    public void setReferer(String referer) {
        this.referer = referer;
    }
}
