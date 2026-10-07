package com.example.llmrouter.apikey;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the admin API, bound from {@code llm.admin.*}. The admin endpoint can't be
 * protected by the API-key filter it's used to bootstrap, so it uses a separate shared secret
 * instead.
 */
@ConfigurationProperties(prefix = "llm.admin")
public class AdminProperties {

    /**
     * Shared secret clients must send in the {@code X-Admin-Secret} header to create API keys.
     * Blank (the default) disables the admin endpoint entirely — every request is rejected,
     * since an unset secret must never be treated as "anything goes".
     */
    private String secret = "";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
