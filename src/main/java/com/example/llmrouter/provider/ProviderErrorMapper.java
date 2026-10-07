package com.example.llmrouter.provider;

import reactor.core.Exceptions;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.concurrent.TimeoutException;

/**
 * Normalizes the various ways a reactive {@code WebClient} call can fail (non-2xx response,
 * connection failure, timeout, anything else) into a single {@link ProviderException} shape, so
 * every real provider adapter (Groq, Gemini, OpenRouter, ...) reports failures identically and
 * {@link com.example.llmrouter.error.GlobalExceptionHandler} only ever has to handle one type.
 */
public final class ProviderErrorMapper {

    private ProviderErrorMapper() {
    }

    public static ProviderException map(String providerName, Throwable error) {
        // Mono#block() wraps checked exceptions (like the TimeoutException raised by the
        // .timeout() operator) in a reactor.core.Exceptions.ReactiveException before rethrowing.
        // Exceptions.unwrap() peels that wrapper off so we can pattern-match the real cause.
        Throwable actual = Exceptions.unwrap(error);

        if (actual instanceof ProviderException providerException) {
            return providerException;
        }
        if (actual instanceof WebClientResponseException responseException) {
            return new ProviderException(
                    providerName,
                    "HTTP " + responseException.getStatusCode().value() + " from " + providerName + ": "
                            + responseException.getResponseBodyAsString(),
                    responseException);
        }
        if (actual instanceof TimeoutException) {
            return new ProviderException(providerName, "Request to " + providerName + " timed out", actual);
        }
        if (actual instanceof WebClientRequestException) {
            return new ProviderException(providerName, "Failed to connect to " + providerName, actual);
        }
        return new ProviderException(providerName, "Unexpected error calling " + providerName, actual);
    }
}
