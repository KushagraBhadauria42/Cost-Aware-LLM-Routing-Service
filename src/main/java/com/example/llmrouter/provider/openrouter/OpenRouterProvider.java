package com.example.llmrouter.provider.openrouter;

import com.example.llmrouter.provider.LlmProvider;
import com.example.llmrouter.provider.ProviderErrorMapper;
import com.example.llmrouter.provider.ProviderException;
import com.example.llmrouter.provider.ProviderResult;
import com.example.llmrouter.provider.openai.OpenAiChatRequest;
import com.example.llmrouter.provider.openai.OpenAiChatResponse;
import com.example.llmrouter.provider.openai.OpenAiMessage;
import com.example.llmrouter.provider.openai.OpenAiResponseMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

/**
 * Calls OpenRouter's free-tier, OpenAI-compatible {@code /chat/completions} endpoint. See
 * {@link com.example.llmrouter.provider.groq.GroqProvider} javadoc for the resilience wiring —
 * identical pattern here, with instance name "openrouter".
 */
@Component
public class OpenRouterProvider implements LlmProvider {

    private static final String PROVIDER_NAME = "openrouter";

    private final WebClient webClient;
    private final OpenRouterProperties properties;

    public OpenRouterProvider(WebClient.Builder webClientBuilder, OpenRouterProperties properties) {
        this.properties = properties;
        WebClient.Builder builder = webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey());
        if (StringUtils.hasText(properties.getReferer())) {
            builder.defaultHeader("HTTP-Referer", properties.getReferer());
        }
        this.webClient = builder.build();
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    @Retry(name = PROVIDER_NAME)
    @CircuitBreaker(name = PROVIDER_NAME, fallbackMethod = "completeFallback")
    public ProviderResult complete(String prompt) {
        OpenAiChatRequest request = new OpenAiChatRequest(
                properties.getModel(),
                List.of(new OpenAiMessage("user", prompt)));

        long startNanos = System.nanoTime();
        try {
            OpenAiChatResponse response = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OpenAiChatResponse.class)
                    .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .block();
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            return OpenAiResponseMapper.toProviderResult(PROVIDER_NAME, response, latencyMs);
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
}
