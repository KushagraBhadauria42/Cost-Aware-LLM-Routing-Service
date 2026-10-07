package com.example.llmrouter.chat;

/**
 * Client-stated routing preference. Stage 1 accepts and echoes this back but does not yet
 * act on it (there is only one provider). Stage 3 introduces the routing engine that chooses
 * a model/provider based on this value.
 */
public enum Priority {
    CHEAP,
    FAST,
    QUALITY,
    AUTO
}
