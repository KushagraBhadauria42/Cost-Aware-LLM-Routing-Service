package com.example.llmrouter.apikey;

import com.example.llmrouter.ratelimit.RateLimiterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers {@link ApiKeyAuthFilter} scoped to {@code /v1/*} only. Deliberately not a
 * {@code @Component}-annotated filter, which Spring Boot would otherwise register for every URL
 * by default ({@code /*}) — that would incorrectly require an API key for {@code /api/ping},
 * {@code /admin/*}, and {@code /actuator/*} too.
 */
@Configuration
public class ApiKeyFilterConfig {

    @Bean
    public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilter(
            ApiKeyService apiKeyService, RateLimiterService rateLimiterService, ObjectMapper objectMapper) {
        FilterRegistrationBean<ApiKeyAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ApiKeyAuthFilter(apiKeyService, rateLimiterService, objectMapper));
        registration.addUrlPatterns("/v1/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
