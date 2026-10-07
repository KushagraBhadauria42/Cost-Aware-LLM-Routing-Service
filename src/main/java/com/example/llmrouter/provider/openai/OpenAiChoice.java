package com.example.llmrouter.provider.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiChoice(OpenAiMessage message) {
}
