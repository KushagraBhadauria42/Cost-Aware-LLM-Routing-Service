package com.example.llmrouter.apikey;

import com.example.llmrouter.chat.ChatRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves API-key authentication and rate limiting/quota enforcement end to end over real HTTP:
 * no key, bad key, a valid key under its limits, a valid key that exceeds its per-minute rate
 * limit, and a valid key that exceeds its daily quota.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "llm.provider.mock.min-latency-ms=0",
        "llm.provider.mock.max-latency-ms=0",
        "llm.provider.mock.failure-rate=0.0",
        "llm.cache.enabled=false"
})
class ApiKeyAuthIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ApiKeyService apiKeyService;

    private HttpEntity<ChatRequest> chatRequest(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        if (apiKey != null) {
            headers.add("X-API-Key", apiKey);
        }
        return new HttpEntity<>(new ChatRequest("hello there", null), headers);
    }

    @Test
    void returns401_whenNoApiKeyHeaderIsPresent() {
        ResponseEntity<String> response = restTemplate.postForEntity("/v1/chat", chatRequest(null), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void returns401_whenApiKeyIsUnknown() {
        ResponseEntity<String> response = restTemplate.postForEntity("/v1/chat", chatRequest("llmr_unknown"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void returns200_whenApiKeyIsValidAndWithinLimits() {
        String rawKey = apiKeyService.createApiKey("integration-ok", 60, 1000).rawKey();

        ResponseEntity<String> response = restTemplate.postForEntity("/v1/chat", chatRequest(rawKey), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void returns429WithRetryAfterHeader_whenPerMinuteRateLimitIsExceeded() {
        String rawKey = apiKeyService.createApiKey("integration-rate-limited", 1, 1000).rawKey();

        ResponseEntity<String> first = restTemplate.postForEntity("/v1/chat", chatRequest(rawKey), String.class);
        ResponseEntity<String> second = restTemplate.postForEntity("/v1/chat", chatRequest(rawKey), String.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(second.getHeaders().getFirst("Retry-After")).isNotBlank();
        assertThat(second.getBody()).contains("Rate limit exceeded");
    }

    @Test
    void returns429WithQuotaMessage_whenDailyQuotaIsExceeded() {
        String rawKey = apiKeyService.createApiKey("integration-quota-exceeded", 1000, 1).rawKey();

        ResponseEntity<String> first = restTemplate.postForEntity("/v1/chat", chatRequest(rawKey), String.class);
        ResponseEntity<String> second = restTemplate.postForEntity("/v1/chat", chatRequest(rawKey), String.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(second.getBody()).contains("Daily quota exceeded");
    }

    @Test
    void adminEndpoint_requiresTheConfiguredSecret() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Admin-Secret", "not-the-real-secret");
        HttpEntity<CreateApiKeyRequest> request = new HttpEntity<>(
                new CreateApiKeyRequest("someone", null, null), headers);

        ResponseEntity<String> response = restTemplate.postForEntity("/admin/api-keys", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
