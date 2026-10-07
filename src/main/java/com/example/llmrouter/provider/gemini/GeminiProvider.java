package com.example.llmrouter.provider.gemini;

import com.example.llmrouter.provider.LlmProvider;
import com.example.llmrouter.provider.ProviderErrorMapper;
import com.example.llmrouter.provider.ProviderException;
import com.example.llmrouter.provider.ProviderResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

/**
 * Calls Google's Gemini free-tier {@code generateContent} endpoint. Unlike Groq/OpenRouter,
 * Gemini is not OpenAI-compatible: the request/response shapes ("contents"/"parts",
 * "candidates") are Gemini-specific, so this adapter has its own DTOs instead of reusing the
 * {@code provider.openai} package. See {@link com.example.llmrouter.provider.groq.GroqProvider}
 * javadoc for the resilience wiring — identical pattern here, with instance name "gemini".
 */
@Component
public class GeminiProvider implements LlmProvider {

    private static final String PROVIDER_NAME = "gemini";

    private final WebClient webClient;
    private final GeminiProperties properties;

    public GeminiProvider(WebClient.Builder webClientBuilder, GeminiProperties properties) {
        this.properties = properties;
        this.webClient = webClientBuilder.baseUrl(properties.getBaseUrl()).build();
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    @Retry(name = PROVIDER_NAME)
    @CircuitBreaker(name = PROVIDER_NAME, fallbackMethod = "completeFallback")
    public ProviderResult complete(String prompt) {
        GeminiRequest request = new GeminiRequest(List.of(new GeminiContent(List.of(new GeminiPart(prompt)))));

        long startNanos = System.nanoTime();
        try {
            GeminiResponse response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/models/{model}:generateContent")
                            .queryParam("key", properties.getApiKey())
                            .build(properties.getModel()))
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(GeminiResponse.class)
                    .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .block();
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            return toProviderResult(response, latencyMs);
        } catch (ProviderException e) {
            throw e;
        } catch (Exception e) {
            throw ProviderErrorMapper.map(PROVIDER_NAME, e);
        }
    }

    private ProviderResult completeFallback(String prompt, Throwable t) {
        if (t instanceof ProviderException providerException) {
            throw providerException;
        }
        throw new ProviderException(PROVIDER_NAME, "Resilience fallback triggered: " + t.getMessage(), t);
    }

    private ProviderResult toProviderResult(GeminiResponse response, long latencyMs) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new ProviderException(PROVIDER_NAME, "Provider returned no candidates");
        }
        GeminiContent content = response.candidates().get(0).content();
        if (content == null || content.parts() == null || content.parts().isEmpty()) {
            throw new ProviderException(PROVIDER_NAME, "Provider returned an empty candidate");
        }
        String text = content.parts().get(0).text();

        GeminiUsageMetadata usage = response.usageMetadata();
        int promptTokens = usage != null ? usage.promptTokenCount() : 0;
        int completionTokens = usage != null ? usage.candidatesTokenCount() : 0;

        return new ProviderResult(text, properties.getModel(), promptTokens, completionTokens, latencyMs);
    }
}
