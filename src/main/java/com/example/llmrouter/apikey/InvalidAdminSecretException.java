package com.example.llmrouter.apikey;

/**
 * Thrown when {@code X-Admin-Secret} is missing, wrong, or the admin secret isn't configured at
 * all. Mapped to a 401 by {@link com.example.llmrouter.error.GlobalExceptionHandler}.
 */
public class InvalidAdminSecretException extends RuntimeException {

    public InvalidAdminSecretException() {
        super("Missing or invalid X-Admin-Secret header");
    }
}
