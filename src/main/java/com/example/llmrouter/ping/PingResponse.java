package com.example.llmrouter.ping;

import java.time.Instant;

/**
 * Simple response payload for the custom liveness endpoint.
 */
public record PingResponse(String status, Instant timestamp) {

    public static PingResponse ok() {
        return new PingResponse("ok", Instant.now());
    }
}
