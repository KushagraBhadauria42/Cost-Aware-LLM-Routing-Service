package com.example.llmrouter.benchmark;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the fixed Stage 9 benchmark prompt set from {@code benchmark/prompts.txt} on the
 * classpath: one prompt per line, blank lines and {@code #}-comment lines ignored. Hand-parsed
 * (not a YAML/JSON list) since prompts are plain free-text, one per line is the simplest format -
 * same "keep parsing dependency-free and explicit" convention {@code ModelRegistry} uses for
 * {@code models.yml}.
 */
@Component
public class BenchmarkPromptLoader {

    private final List<String> prompts;

    public BenchmarkPromptLoader() {
        this("benchmark/prompts.txt");
    }

    /** Package-private: lets tests load a fixed prompt file without touching the real resource. */
    BenchmarkPromptLoader(String resourceName) {
        this.prompts = List.copyOf(loadFromClasspath(resourceName));
    }

    public List<String> prompts() {
        return prompts;
    }

    private static List<String> loadFromClasspath(String resourceName) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(resourceName).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                lines.add(trimmed);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + resourceName + " from classpath", e);
        }
        if (lines.isEmpty()) {
            throw new IllegalStateException(resourceName + " contained no usable prompts");
        }
        return lines;
    }
}
