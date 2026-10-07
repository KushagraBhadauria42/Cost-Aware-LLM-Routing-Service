package com.example.llmrouter.provider.openai;

import com.example.llmrouter.provider.ProviderException;
import com.example.llmrouter.provider.ProviderResult;

/**
 * Maps an {@link OpenAiChatResponse} (as returned by Groq and OpenRouter) into our unified
 * {@link ProviderResult}, shared so both adapters don't duplicate the same null-checks and
 * field extraction.
 */
public final class OpenAiResponseMapper {

    private OpenAiResponseMapper() {
    }

    public static ProviderResult toProviderResult(String providerName, OpenAiChatResponse response, long latencyMs) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new ProviderException(providerName, "Provider returned no choices");
        }
        OpenAiMessage message = response.choices().get(0).message();
        if (message == null || message.content() == null) {
            throw new ProviderException(providerName, "Provider returned an empty message");
        }

        OpenAiUsage usage = response.usage();
        int promptTokens = usage != null ? usage.promptTokens() : 0;
        int completionTokens = usage != null ? usage.completionTokens() : 0;

        return new ProviderResult(message.content(), response.model(), promptTokens, completionTokens, latencyMs);
    }
}
