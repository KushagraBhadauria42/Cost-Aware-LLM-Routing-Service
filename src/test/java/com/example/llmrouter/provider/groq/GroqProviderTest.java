package com.example.llmrouter.provider.groq;

import com.example.llmrouter.provider.ProviderException;
import com.example.llmrouter.provider.ProviderResult;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.reactive.function.client.WebClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@link GroqProvider} against a local WireMock server standing in for Groq's real API
 * — no network calls are ever made in this test.
 */
class GroqProviderTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private GroqProperties properties;
    private GroqProvider provider;

    @BeforeEach
    void setUp() {
        properties = new GroqProperties();
        properties.setBaseUrl(wireMock.baseUrl());
        properties.setApiKey("test-key");
        properties.setModel("llama-3.1-8b-instant");
        properties.setTimeoutMs(5_000);
        provider = new GroqProvider(WebClient.builder(), properties);
    }

    @Test
    void mapsSuccessfulResponseToProviderResult() {
        wireMock.stubFor(post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "model": "llama-3.1-8b-instant",
                                  "choices": [{"message": {"role": "assistant", "content": "Hello there"}}],
                                  "usage": {"prompt_tokens": 5, "completion_tokens": 3, "total_tokens": 8}
                                }
                                """)));

        ProviderResult result = provider.complete("Hi");

        assertThat(result.text()).isEqualTo("Hello there");
        assertThat(result.model()).isEqualTo("llama-3.1-8b-instant");
        assertThat(result.promptTokens()).isEqualTo(5);
        assertThat(result.completionTokens()).isEqualTo(3);
        assertThat(provider.name()).isEqualTo("groq");
    }

    @Test
    void mapsNon2xxResponseToProviderException() {
        wireMock.stubFor(post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse().withStatus(401).withBody("{\"error\":\"invalid api key\"}")));

        assertThatThrownBy(() -> provider.complete("Hi"))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("401");
    }

    @Test
    void mapsTimeoutToProviderException() {
        properties.setTimeoutMs(200);
        wireMock.stubFor(post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse().withFixedDelay(500).withStatus(200).withBody("{}")));

        assertThatThrownBy(() -> provider.complete("Hi"))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("timed out");
    }
}
