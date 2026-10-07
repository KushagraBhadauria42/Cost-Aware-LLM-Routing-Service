package com.example.llmrouter.provider.openrouter;

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
 * Exercises {@link OpenRouterProvider} against a local WireMock server standing in for
 * OpenRouter's real API — no network calls are ever made in this test.
 */
class OpenRouterProviderTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private OpenRouterProperties properties;
    private OpenRouterProvider provider;

    @BeforeEach
    void setUp() {
        properties = new OpenRouterProperties();
        properties.setBaseUrl(wireMock.baseUrl());
        properties.setApiKey("test-key");
        properties.setModel("meta-llama/llama-3.1-8b-instruct:free");
        properties.setTimeoutMs(5_000);
        provider = new OpenRouterProvider(WebClient.builder(), properties);
    }

    @Test
    void mapsSuccessfulResponseToProviderResult() {
        wireMock.stubFor(post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "model": "meta-llama/llama-3.1-8b-instruct:free",
                                  "choices": [{"message": {"role": "assistant", "content": "Hi from OpenRouter"}}],
                                  "usage": {"prompt_tokens": 4, "completion_tokens": 6, "total_tokens": 10}
                                }
                                """)));

        ProviderResult result = provider.complete("Hi");

        assertThat(result.text()).isEqualTo("Hi from OpenRouter");
        assertThat(result.promptTokens()).isEqualTo(4);
        assertThat(result.completionTokens()).isEqualTo(6);
        assertThat(provider.name()).isEqualTo("openrouter");
    }

    @Test
    void mapsNon2xxResponseToProviderException() {
        wireMock.stubFor(post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse().withStatus(429).withBody("{\"error\":\"rate limited\"}")));

        assertThatThrownBy(() -> provider.complete("Hi"))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("429");
    }

    @Test
    void mapsEmptyChoicesToProviderException() {
        wireMock.stubFor(post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"model\": \"x\", \"choices\": [], \"usage\": {\"prompt_tokens\":0,\"completion_tokens\":0,\"total_tokens\":0}}")));

        assertThatThrownBy(() -> provider.complete("Hi"))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("no choices");
    }
}
