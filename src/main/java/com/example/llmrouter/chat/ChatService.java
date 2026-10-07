package com.example.llmrouter.chat;

import com.example.llmrouter.apikey.ApiKey;
import com.example.llmrouter.cache.CachedChatPayload;
import com.example.llmrouter.cache.ChatCacheService;
import com.example.llmrouter.provider.LlmProvider;
import com.example.llmrouter.provider.ProviderException;
import com.example.llmrouter.provider.ProviderResult;
import com.example.llmrouter.routing.ModelSpec;
import com.example.llmrouter.routing.RoutingDecision;
import com.example.llmrouter.routing.RoutingEngine;
import com.example.llmrouter.usage.UsageLogService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates a single chat completion request. First checks the Redis exact-match cache
 * ({@link ChatCacheService}, keyed on normalized prompt + priority); on a hit, returns instantly
 * with zero provider calls. On a miss, asks the {@link RoutingEngine} for every candidate model
 * ranked best-to-worst for the request's priority, then tries each candidate's {@link LlmProvider}
 * bean in order. Each provider is already wrapped by Resilience4j (retry + circuit breaker,
 * configured per provider in {@code application.yml}), so a single {@code provider.complete(...)}
 * call here already reflects retries exhausted / circuit open. If a candidate fails, the
 * next-best candidate from the routing strategy is tried — this is the fallback chain. Whatever
 * answer is ultimately produced (first try or fallback) is cached identically before being
 * returned, so a later identical request can be served from cache regardless of which provider
 * originally answered it.
 */
@Service
public class ChatService {

    private final RoutingEngine routingEngine;
    private final Map<String, LlmProvider> providersByName;
    private final ChatCacheService chatCacheService;
    private final UsageLogService usageLogService;

    public ChatService(RoutingEngine routingEngine, List<LlmProvider> providers, ChatCacheService chatCacheService,
            UsageLogService usageLogService) {
        this.routingEngine = routingEngine;
        this.providersByName = providers.stream()
                .collect(Collectors.toMap(LlmProvider::name, Function.identity()));
        this.chatCacheService = chatCacheService;
        this.usageLogService = usageLogService;
    }

    public ChatResponse handle(ChatRequest request, ApiKey apiKey) {
        Optional<CachedChatPayload> cached = chatCacheService.get(request.prompt(), request.priority());
        if (cached.isPresent()) {
            ChatResponse response = toResponse(cached.get(), request.priority(), 0, true);
            usageLogService.record(apiKey, request.priority(), response);
            return response;
        }

        RoutingDecision primaryDecision = routingEngine.route(request.prompt(), request.priority());
        List<ModelSpec> ranked = routingEngine.rank(request.prompt(), request.priority());

        List<String> failures = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            ModelSpec candidate = ranked.get(i);
            LlmProvider provider = providersByName.get(candidate.provider());
            if (provider == null) {
                failures.add(candidate.provider() + ": no LlmProvider bean registered");
                continue;
            }

            try {
                ProviderResult result = provider.complete(request.prompt());
                boolean fallbackUsed = !failures.isEmpty();
                String fallbackReason = fallbackUsed
                        ? "primary candidate(s) failed (" + String.join("; ", failures) + "); fell back to "
                                + candidate.provider() + "/" + candidate.model()
                        : null;

                CachedChatPayload payload = new CachedChatPayload(
                        result.text(),
                        provider.name(),
                        result.model(),
                        result.promptTokens(),
                        result.completionTokens(),
                        result.totalTokens(),
                        primaryDecision.reason(),
                        fallbackUsed,
                        fallbackReason);
                chatCacheService.put(request.prompt(), request.priority(), payload);

                ChatResponse response = toResponse(payload, request.priority(), result.latencyMs(), false);
                usageLogService.record(apiKey, request.priority(), response);
                return response;
            } catch (ProviderException e) {
                failures.add(candidate.provider() + ": " + e.getMessage());
            }
        }

        throw new ProviderException(
                ranked.get(ranked.size() - 1).provider(),
                "All " + ranked.size() + " candidate provider(s) failed: " + String.join("; ", failures));
    }

    private static ChatResponse toResponse(CachedChatPayload payload, Priority priority, long latencyMs,
            boolean cacheHit) {
        return new ChatResponse(
                payload.responseText(),
                payload.provider(),
                payload.model(),
                payload.promptTokens(),
                payload.completionTokens(),
                payload.totalTokens(),
                latencyMs,
                priority,
                payload.routingReason(),
                payload.fallbackUsed(),
                payload.fallbackReason(),
                cacheHit);
    }
}
