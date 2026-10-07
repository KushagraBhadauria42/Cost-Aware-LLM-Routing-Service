package com.example.llmrouter.provider.gemini;

import com.example.llmrouter.provider.ProviderException;
import com.example.llmrouter.provider.ProviderResult;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.reactive.function.client.WebClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@link GeminiProvider} against a local WireMock server standing in for Gemini's real
 * API — no network calls are ever made in this test.
 */
class GeminiProviderTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private GeminiProperties properties;
    private GeminiProvider provider;

    @BeforeEach
    void setUp() {
        properties = new GeminiProperties();
        properties.setBaseUrl(wireMock.baseUrl());
        properties.setApiKey("test-key");
        properties.setModel("gemini-1.5-flash");
        properties.setTimeoutMs(5_000);
        provider = new GeminiProvider(WebClient.builder(), properties);
    }

    @Test
    void mapsSuccessfulResponseToProviderResult() {
        wireMock.stubFor(post(urlPathEqualTo("/models/gemini-1.5-flash:generateContent"))
                .withQueryParam("key", equalTo("test-key"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "candidates": [{"content": {"parts": [{"text": "Hi from Gemini"}]}}],
                                  "usageMetadata": {"promptTokenCount": 4, "candidatesTokenCount": 5, "totalTokenCount": 9}
                                }
                                """)));

        ProviderResult result = provider.complete("Hi");

        assertThat(result.text()).isEqualTo("Hi from Gemini");
        assertThat(result.model()).isEqualTo("gemini-1.5-flash");
        assertThat(result.promptTokens()).isEqualTo(4);
        assertThat(result.completionTokens()).isEqualTo(5);
        assertThat(provider.name()).isEqualTo("gemini");
    }

    @Test
    void mapsNon2xxResponseToProviderException() {
        wireMock.stubFor(post(urlPathEqualTo("/models/gemini-1.5-flash:generateContent"))
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":{\"message\":\"bad request\"}}")));

        assertThatThrownBy(() -> provider.complete("Hi"))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("400");
    }

    @Test
    void mapsTimeoutToProviderException() {
        properties.setTimeoutMs(200);
        wireMock.stubFor(post(urlPathEqualTo("/models/gemini-1.5-flash:generateContent"))
                .willReturn(aResponse().withFixedDelay(500).withStatus(200).withBody("{}")));

        assertThatThrownBy(() -> provider.complete("Hi"))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("timed out");
    }
}
