package com.example.llmrouter.provider.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A single chat message in the OpenAI-compatible format: {@code role} is one of
 * "system"/"user"/"assistant", {@code content} is the plain text.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiMessage(String role, String content) {
}
