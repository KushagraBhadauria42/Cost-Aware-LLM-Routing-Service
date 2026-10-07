package com.example.llmrouter.provider.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiResponse(List<GeminiCandidate> candidates, GeminiUsageMetadata usageMetadata) {
}
