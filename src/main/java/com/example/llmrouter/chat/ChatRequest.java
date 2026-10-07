package com.example.llmrouter.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /v1/chat}.
 *
 * @param prompt   the user prompt; required, bounded to keep mock token estimates and real
 *                 provider costs sane
 * @param priority routing preference; defaults to {@link Priority#AUTO} when omitted
 */
public record ChatRequest(

        @NotBlank(message = "prompt must not be blank")
        @Size(max = 4000, message = "prompt must be at most 4000 characters")
        String prompt,

        Priority priority) {

    public ChatRequest {
        if (priority == null) {
            priority = Priority.AUTO;
        }
    }
}
