package com.example.llmrouter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the full Spring context starts against a real (Testcontainers) Postgres instance,
 * with Flyway migrations applied. Redis auto-configuration only requires a reachable host:port
 * at bean-creation time, not at context startup, so it is not started here.
 */
@Testcontainers
@SpringBootTest
class LlmRouterApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void contextLoads() {
        // If the Spring context fails to start (bad config, unreachable DB, failed Flyway
        // migration, etc.) this test fails automatically.
    }
}
