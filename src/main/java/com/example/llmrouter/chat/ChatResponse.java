package com.example.llmrouter.chat;

/**
 * Response body for {@code POST /v1/chat}.
 *
 * @param responseText   the generated text
 * @param provider       which provider served the request (e.g. "mock", "groq")
 * @param model          the concrete model used
 * @param promptTokens   tokens consumed by the prompt
 * @param completionTokens tokens generated in the response
 * @param totalTokens    promptTokens + completionTokens
 * @param latencyMs      time the provider call took, in milliseconds
 * @param priorityUsed   the routing preference that was applied (echoes the request, defaulted to AUTO)
 * @param routingReason  human-readable explanation of why this model was chosen, from the routing engine
 * @param fallbackUsed   true if the first-choice provider failed and a lower-ranked candidate served the request
 * @param fallbackReason human-readable explanation of what failed and which candidate was used instead;
 *                       {@code null} when {@code fallbackUsed} is false
 * @param cacheHit       true if this response was served from the Redis exact-match cache instead of
 *                       calling a provider; when true, {@code latencyMs} is always {@code 0}
 */
public record ChatResponse(
        String responseText,
        String provider,
        String model,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long latencyMs,
        Priority priorityUsed,
        String routingReason,
        boolean fallbackUsed,
        String fallbackReason,
        boolean cacheHit) {
}
