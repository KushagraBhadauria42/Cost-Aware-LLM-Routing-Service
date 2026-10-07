package com.example.llmrouter.chat;

import com.example.llmrouter.apikey.ApiKey;
import com.example.llmrouter.cache.CachedChatPayload;
import com.example.llmrouter.cache.ChatCacheService;
import com.example.llmrouter.provider.LlmProvider;
import com.example.llmrouter.provider.ProviderException;
import com.example.llmrouter.provider.ProviderResult;
import com.example.llmrouter.routing.ModelSpec;
import com.example.llmrouter.routing.QualityTier;
import com.example.llmrouter.routing.RoutingDecision;
import com.example.llmrouter.routing.RoutingEngine;
import com.example.llmrouter.usage.UsageLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private RoutingEngine routingEngine;

    @Mock
    private LlmProvider provider;

    @Mock
    private LlmProvider secondProvider;

    @Mock
    private ChatCacheService chatCacheService;

    @Mock
    private UsageLogService usageLogService;

    private final ApiKey apiKey = mock(ApiKey.class);

    @Test
    void routesThenMapsProviderResultIntoChatResponse_includingRoutingReason() {
        ModelSpec chosen = new ModelSpec("mock", "mock-model-v1", 0.0, 150, QualityTier.MEDIUM);
        when(chatCacheService.get("hello", Priority.QUALITY)).thenReturn(Optional.empty());
        when(routingEngine.route("hello", Priority.QUALITY))
                .thenReturn(new RoutingDecision(chosen, "test reason"));
        when(routingEngine.rank("hello", Priority.QUALITY)).thenReturn(List.of(chosen));
        when(provider.name()).thenReturn("mock");
        when(provider.complete("hello"))
                .thenReturn(new ProviderResult("hi there", "mock-model-v1", 3, 5, 42));

        ChatService chatService = new ChatService(routingEngine, List.of(provider), chatCacheService, usageLogService);
        ChatResponse response = chatService.handle(new ChatRequest("hello", Priority.QUALITY), apiKey);

        assertThat(response.responseText()).isEqualTo("hi there");
        assertThat(response.provider()).isEqualTo("mock");
        assertThat(response.model()).isEqualTo("mock-model-v1");
        assertThat(response.promptTokens()).isEqualTo(3);
        assertThat(response.completionTokens()).isEqualTo(5);
        assertThat(response.totalTokens()).isEqualTo(8);
        assertThat(response.latencyMs()).isEqualTo(42);
        assertThat(response.priorityUsed()).isEqualTo(Priority.QUALITY);
        assertThat(response.routingReason()).isEqualTo("test reason");
        assertThat(response.fallbackUsed()).isFalse();
        assertThat(response.fallbackReason()).isNull();
        assertThat(response.cacheHit()).isFalse();
        verify(chatCacheService).put("hello", Priority.QUALITY,
                new CachedChatPayload("hi there", "mock", "mock-model-v1", 3, 5, 8, "test reason", false, null));
        verify(usageLogService).record(apiKey, Priority.QUALITY, response);
    }

    @Test
    void returnsCachedResponseWithZeroLatency_andNeverCallsAProvider_onCacheHit() {
        CachedChatPayload cached = new CachedChatPayload(
                "hi there", "mock", "mock-model-v1", 3, 5, 8, "test reason", false, null);
        when(chatCacheService.get("hello", Priority.QUALITY)).thenReturn(Optional.of(cached));

        ChatService chatService = new ChatService(routingEngine, List.of(provider), chatCacheService, usageLogService);
        ChatResponse response = chatService.handle(new ChatRequest("hello", Priority.QUALITY), apiKey);

        assertThat(response.cacheHit()).isTrue();
        assertThat(response.latencyMs()).isZero();
        assertThat(response.responseText()).isEqualTo("hi there");
        assertThat(response.provider()).isEqualTo("mock");
        assertThat(response.priorityUsed()).isEqualTo(Priority.QUALITY);
        verify(provider, never()).complete(anyString());
        verify(routingEngine, never()).route(anyString(), any());
        verify(routingEngine, never()).rank(anyString(), any());
        verify(chatCacheService, never()).put(anyString(), any(), any());
        verify(usageLogService).record(apiKey, Priority.QUALITY, response);
    }

    @Test
    void fallsBackToNextRankedCandidate_whenTopChoiceProviderFails() {
        ModelSpec primary = new ModelSpec("gemini", "gemini-1.5-flash", 0.02, 600, QualityTier.HIGH);
        ModelSpec fallback = new ModelSpec("mock", "mock-model-v1", 0.0, 150, QualityTier.MEDIUM);
        when(chatCacheService.get(anyString(), any())).thenReturn(Optional.empty());
        when(routingEngine.route(anyString(), any())).thenReturn(new RoutingDecision(primary, "quality strategy reason"));
        when(routingEngine.rank(anyString(), any())).thenReturn(List.of(primary, fallback));
        when(provider.name()).thenReturn("gemini");
        when(provider.complete("hello")).thenThrow(new ProviderException("gemini", "circuit breaker open"));
        when(secondProvider.name()).thenReturn("mock");
        when(secondProvider.complete("hello"))
                .thenReturn(new ProviderResult("hi there", "mock-model-v1", 3, 5, 10));

        ChatService chatService = new ChatService(routingEngine, List.of(provider, secondProvider), chatCacheService, usageLogService);
        ChatResponse response = chatService.handle(new ChatRequest("hello", Priority.QUALITY), apiKey);

        assertThat(response.provider()).isEqualTo("mock");
        assertThat(response.fallbackUsed()).isTrue();
        assertThat(response.fallbackReason())
                .contains("gemini").contains("circuit breaker open").contains("fell back to mock");
        // the reason for the *original* routing choice is still reported, even though it failed
        assertThat(response.routingReason()).isEqualTo("quality strategy reason");
        // a fallback-obtained answer is cached exactly like a first-try success
        verify(chatCacheService).put("hello", Priority.QUALITY,
                new CachedChatPayload("hi there", "mock", "mock-model-v1", 3, 5, 8,
                        "quality strategy reason", true, response.fallbackReason()));
        verify(usageLogService).record(apiKey, Priority.QUALITY, response);
    }

    @Test
    void throwsProviderException_whenEveryRankedCandidateFails() {
        ModelSpec primary = new ModelSpec("gemini", "gemini-1.5-flash", 0.02, 600, QualityTier.HIGH);
        ModelSpec fallback = new ModelSpec("mock", "mock-model-v1", 0.0, 150, QualityTier.MEDIUM);
        when(chatCacheService.get(anyString(), any())).thenReturn(Optional.empty());
        when(routingEngine.route(anyString(), any())).thenReturn(new RoutingDecision(primary, "reason"));
        when(routingEngine.rank(anyString(), any())).thenReturn(List.of(primary, fallback));
        when(provider.name()).thenReturn("gemini");
        when(provider.complete("hello")).thenThrow(new ProviderException("gemini", "timed out"));
        when(secondProvider.name()).thenReturn("mock");
        when(secondProvider.complete("hello")).thenThrow(new ProviderException("mock", "simulated failure"));

        ChatService chatService = new ChatService(routingEngine, List.of(provider, secondProvider), chatCacheService, usageLogService);

        assertThatThrownBy(() -> chatService.handle(new ChatRequest("hello", Priority.QUALITY), apiKey))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("gemini").hasMessageContaining("timed out")
                .hasMessageContaining("mock").hasMessageContaining("simulated failure");
        // nothing succeeded, so nothing should be cached or logged
        verify(chatCacheService, never()).put(anyString(), any(), any());
        verify(usageLogService, never()).record(any(), any(), any());
    }

    @Test
    void throwsProviderException_whenNoProviderBeanMatchesTheChosenModel() {
        ModelSpec chosen = new ModelSpec("groq", "llama-3.1-8b-instant", 0.05, 400, QualityTier.MEDIUM);
        when(chatCacheService.get(anyString(), any())).thenReturn(Optional.empty());
        when(routingEngine.route(anyString(), any())).thenReturn(new RoutingDecision(chosen, "reason"));
        when(routingEngine.rank(anyString(), any())).thenReturn(List.of(chosen));

        ChatService chatService = new ChatService(routingEngine, List.of(), chatCacheService, usageLogService);

        assertThatThrownBy(() -> chatService.handle(new ChatRequest("hello", Priority.FAST), apiKey))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("groq");
    }

    @Test
    void defaultsPriorityToAuto_whenNotSpecified() {
        ChatRequest request = new ChatRequest("hello", null);

        assertThat(request.priority()).isEqualTo(Priority.AUTO);
    }
}

