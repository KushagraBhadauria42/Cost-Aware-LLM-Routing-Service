package com.example.llmrouter.usage;

import com.example.llmrouter.apikey.ApiKeyService;
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
 * Proves usage logging + {@code GET /v1/usage} end to end over real HTTP: a cache miss and a
 * cache hit for the same prompt both produce a row, and the summary reflects an accurate hit
 * rate, virtual cost, baseline cost, and savings.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "llm.provider.mock.min-latency-ms=0",
        "llm.provider.mock.max-latency-ms=0",
        "llm.provider.mock.failure-rate=0.0"
})
class UsageIntegrationTest {

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

    private HttpEntity<ChatRequest> chatRequest(String apiKey, String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-API-Key", apiKey);
        return new HttpEntity<>(new ChatRequest(prompt, null), headers);
    }

    private HttpEntity<Void> usageRequest(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-API-Key", apiKey);
        return new HttpEntity<>(headers);
    }

    @Test
    void usageSummary_reflectsACacheMissThenACacheHit_forTheSamePrompt() {
        String rawKey = apiKeyService.createApiKey("usage-client", 60, 1000).rawKey();

        ResponseEntity<String> first = restTemplate.postForEntity(
                "/v1/chat", chatRequest(rawKey, "usage test prompt"), String.class);
        ResponseEntity<String> second = restTemplate.postForEntity(
                "/v1/chat", chatRequest(rawKey, "usage test prompt"), String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).contains("\"cacheHit\":true");

        ResponseEntity<UsageSummaryResponse> usage = restTemplate.exchange(
                "/v1/usage", org.springframework.http.HttpMethod.GET, usageRequest(rawKey), UsageSummaryResponse.class);

        assertThat(usage.getStatusCode()).isEqualTo(HttpStatus.OK);
        ClientUsageSummary client = usage.getBody().client();
        assertThat(client.clientName()).isEqualTo("usage-client");
        assertThat(client.totalRequests()).isEqualTo(2L);
        assertThat(client.cacheHits()).isEqualTo(1L);
        assertThat(client.cacheHitRatePercent()).isEqualTo(50.0);
        // mock provider is free (price 0.0/1k), so virtual cost is zero either way here, but
        // baseline cost (most expensive configured model) must still be positive for both rows.
        assertThat(client.totalBaselineCost()).isGreaterThan(0.0);
        assertThat(usage.getBody().byModel()).isNotEmpty();
    }

    @Test
    void usage_requiresAnApiKey_likeAnyOtherV1Endpoint() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/v1/usage", org.springframework.http.HttpMethod.GET, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
