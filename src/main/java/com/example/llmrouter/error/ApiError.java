package com.example.llmrouter.error;

import java.time.Instant;
import java.util.Map;

/**
 * Uniform error payload returned by every failure path in the API.
 *
 * @param fieldErrors per-field validation messages; null when the error isn't a validation error
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, null);
    }

    public static ApiError of(int status, String error, String message, String path, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, path, fieldErrors);
    }
}
