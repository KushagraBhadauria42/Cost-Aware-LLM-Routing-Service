package com.example.llmrouter.provider.groq;

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
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

/**
 * Calls Groq's free-tier, OpenAI-compatible {@code /chat/completions} endpoint. Selected by the
 * {@link com.example.llmrouter.routing.RoutingEngine} alongside the other {@link LlmProvider}
 * beans. {@link #complete(String)} is wrapped by Resilience4j retry + circuit breaker
 * (instance name "groq", configured in {@code application.yml}); on exhausted retries or an open
 * circuit, {@link #completeFallback} converts the failure into a plain {@link ProviderException}
 * so {@code ChatService} can fall back to the next-ranked candidate.
 */
@Component
public class GroqProvider implements LlmProvider {

    private static final String PROVIDER_NAME = "groq";

    private final WebClient webClient;
    private final GroqProperties properties;

    public GroqProvider(WebClient.Builder webClientBuilder, GroqProperties properties) {
        this.properties = properties;
        this.webClient = webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .build();
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

    /**
     * Invoked by the Resilience4j circuit breaker aspect whenever {@link #complete(String)}
     * ultimately fails (retries exhausted, or the circuit is open so the call was never
     * attempted). Normalizes every failure mode into a {@link ProviderException} so callers only
     * ever need to handle one exception type.
     */
    private ProviderResult completeFallback(String prompt, Throwable t) {
        if (t instanceof ProviderException providerException) {
            throw providerException;
        }
        throw new ProviderException(PROVIDER_NAME, "Resilience fallback triggered: " + t.getMessage(), t);
    }
}
