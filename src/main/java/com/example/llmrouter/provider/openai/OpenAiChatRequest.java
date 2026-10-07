package com.example.llmrouter.provider.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Request body shared by any provider that exposes an OpenAI-compatible
 * {@code /chat/completions} endpoint (Groq, OpenRouter).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiChatRequest(String model, List<OpenAiMessage> messages) {
}
