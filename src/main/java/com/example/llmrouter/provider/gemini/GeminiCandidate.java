package com.example.llmrouter.provider.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiCandidate(GeminiContent content) {
}
