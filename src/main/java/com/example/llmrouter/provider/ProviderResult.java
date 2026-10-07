package com.example.llmrouter.provider;

/**
 * The result of a single completion call made to an LLM provider.
 *
 * @param text             the generated response text
 * @param model            the concrete model name that served the request (e.g. "llama-3.1-8b-instant")
 * @param promptTokens     tokens consumed by the prompt (estimated for the mock provider, exact for real ones)
 * @param completionTokens tokens generated in the response
 * @param latencyMs        wall-clock time the provider call took, in milliseconds
 */
public record ProviderResult(
        String text,
        String model,
        int promptTokens,
        int completionTokens,
        long latencyMs) {

    public int totalTokens() {
        return promptTokens + completionTokens;
    }
}
