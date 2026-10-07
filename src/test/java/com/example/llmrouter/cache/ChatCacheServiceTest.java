package com.example.llmrouter.cache;

import com.example.llmrouter.apikey.ApiKeyRepository;
import com.example.llmrouter.chat.Priority;
import com.example.llmrouter.usage.UsageLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link ChatCacheService} against a real Redis (Testcontainers), proving the hit,
 * miss, and TTL-expiry behavior end to end. Datasource/JPA/Flyway are excluded so this test
 * doesn't also need a Postgres container; Redis autoconfiguration is left enabled and pointed
 * at the container via {@link DynamicPropertySource}. {@link ApiKeyRepository} and
 * {@link UsageLogRepository} are mocked out since they're JPA repositories and can't be created
 * without a real {@code DataSource}.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
class ChatCacheServiceTest {

    @MockitoBean
    private ApiKeyRepository apiKeyRepository;

    @MockitoBean
    private UsageLogRepository usageLogRepository;

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private ChatCacheService chatCacheService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final CachedChatPayload PAYLOAD = new CachedChatPayload(
            "hi there", "mock", "mock-model-v1", 3, 5, 8, "test reason", false, null);

    @Test
    void getReturnsEmpty_whenNothingHasBeenCachedForThatPromptAndPriority() {
        Optional<CachedChatPayload> result = chatCacheService.get("a prompt nobody has asked yet", Priority.CHEAP);

        assertThat(result).isEmpty();
    }

    @Test
    void getReturnsTheCachedPayload_afterAPutForTheSamePromptAndPriority() {
        chatCacheService.put("what is the capital of France?", Priority.QUALITY, PAYLOAD);

        Optional<CachedChatPayload> result = chatCacheService.get("what is the capital of France?", Priority.QUALITY);

        assertThat(result).contains(PAYLOAD);
    }

    @Test
    void getReturnsEmpty_forTheSamePromptUnderADifferentPriority() {
        chatCacheService.put("explain photosynthesis", Priority.FAST, PAYLOAD);

        Optional<CachedChatPayload> result = chatCacheService.get("explain photosynthesis", Priority.QUALITY);

        assertThat(result).isEmpty();
    }

    @Test
    void getReturnsTheCachedPayload_forAPromptThatOnlyDiffersByCasingOrWhitespace() {
        chatCacheService.put("Tell Me A Joke", Priority.CHEAP, PAYLOAD);

        Optional<CachedChatPayload> result = chatCacheService.get("  tell   me a joke  ", Priority.CHEAP);

        assertThat(result).contains(PAYLOAD);
    }

    @Test
    void entryExpiresAfterItsConfiguredTtl() throws InterruptedException {
        // A second ChatCacheService sharing the same (Spring-managed) Redis connection and
        // ObjectMapper, but with a 1-second TTL instead of the default 600s, so this test
        // doesn't need to wait minutes to observe expiry.
        ChatCacheProperties shortTtlProperties = new ChatCacheProperties();
        shortTtlProperties.setTtlSeconds(1);
        ChatCacheService shortTtlCache = new ChatCacheService(redisTemplate, objectMapper, shortTtlProperties);

        shortTtlCache.put("ephemeral prompt", Priority.FAST, PAYLOAD);
        assertThat(shortTtlCache.get("ephemeral prompt", Priority.FAST)).contains(PAYLOAD);

        Thread.sleep(1500);

        assertThat(shortTtlCache.get("ephemeral prompt", Priority.FAST)).isEmpty();
    }

    @Test
    void doesNotReadOrWrite_whenCachingIsDisabled() {
        ChatCacheProperties disabledProperties = new ChatCacheProperties();
        disabledProperties.setEnabled(false);
        ChatCacheService disabledCache = new ChatCacheService(redisTemplate, objectMapper, disabledProperties);

        disabledCache.put("should never be stored", Priority.AUTO, PAYLOAD);

        assertThat(disabledCache.get("should never be stored", Priority.AUTO)).isEmpty();
        assertThat(chatCacheService.get("should never be stored", Priority.AUTO)).isEmpty();
    }

    @Test
    void buildCacheKey_isCaseAndWhitespaceInsensitiveForThePromptButDistinguishesPriority() {
        String keyA = ChatCacheService.buildCacheKey("Hello World", Priority.FAST);
        String keyB = ChatCacheService.buildCacheKey("  hello   world  ", Priority.FAST);
        String keyC = ChatCacheService.buildCacheKey("Hello World", Priority.CHEAP);

        assertThat(keyA).isEqualTo(keyB);
        assertThat(keyA).isNotEqualTo(keyC);
    }
}
