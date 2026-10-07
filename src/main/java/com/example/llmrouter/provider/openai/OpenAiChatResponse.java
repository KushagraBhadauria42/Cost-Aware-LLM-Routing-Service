package com.example.llmrouter.provider.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Response body shared by any provider that exposes an OpenAI-compatible
 * {@code /chat/completions} endpoint. {@code @JsonIgnoreProperties(ignoreUnknown = true)} lets us
 * only model the fields we actually use (id, object, created, system_fingerprint, etc. are
 * silently dropped) so provider-side additions never break deserialization.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiChatResponse(String model, List<OpenAiChoice> choices, OpenAiUsage usage) {
}
