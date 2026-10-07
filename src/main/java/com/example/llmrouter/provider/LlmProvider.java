package com.example.llmrouter.provider;

/**
 * Abstraction over any LLM backend (Groq, Gemini, OpenRouter, or the built-in mock).
 * Stage 1 only has one implementation ({@code MockProvider}); Stage 2 adds real HTTP-backed
 * providers; Stage 3 introduces a registry/routing engine that chooses between them.
 */
public interface LlmProvider {

    /**
     * Short, stable identifier used in responses, logs and metrics (e.g. "mock", "groq").
     */
    String name();

    /**
     * Executes a single completion call for the given prompt.
     *
     * @throws ProviderException if the provider fails to produce a result
     */
    ProviderResult complete(String prompt);
}
