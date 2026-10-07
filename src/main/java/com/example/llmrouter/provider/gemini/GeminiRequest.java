package com.example.llmrouter.provider.gemini;

import java.util.List;

/** Gemini's {@code generateContent} request body: a flat list of content turns. */
public record GeminiRequest(List<GeminiContent> contents) {
}
