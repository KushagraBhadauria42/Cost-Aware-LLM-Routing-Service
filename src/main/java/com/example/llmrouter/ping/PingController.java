package com.example.llmrouter.ping;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal liveness endpoint, separate from Spring Boot Actuator's /actuator/health.
 * Useful for a quick manual sanity check that the application context started.
 */
@RestController
public class PingController {

    @GetMapping("/api/ping")
    public PingResponse ping() {
        return PingResponse.ok();
    }
}
