package com.example.llmrouter.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the exact-match chat response cache, bound from {@code llm.cache.*}.
 */
@ConfigurationProperties(prefix = "llm.cache")
public class ChatCacheProperties {

    /** Master switch; when {@code false}, {@link ChatCacheService} never reads or writes Redis. */
    private boolean enabled = true;

    /** How long a cached response stays valid before Redis expires it automatically. */
    private long ttlSeconds = 600;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }
}
