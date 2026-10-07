package com.example.llmrouter.routing;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads the available {@link ModelSpec}s from {@code models.yml} on the classpath.
 * <p>
 * Parsed by hand into a generic {@code Map} (via SnakeYAML, already on the classpath through
 * {@code spring-boot-starter}) rather than bean-binding or a Jackson YAML dependency — keeps the
 * mapping explicit and dependency-free, consistent with how the provider adapters hand-map their
 * JSON responses.
 */
@Component
public class ModelRegistry {

    private final List<ModelSpec> models;

    public ModelRegistry() {
        this(loadFromClasspath("models.yml"));
    }

    /** Package-private: lets tests build a registry from a fixed in-memory list. */
    ModelRegistry(List<ModelSpec> models) {
        this.models = List.copyOf(models);
    }

    public List<ModelSpec> all() {
        return models;
    }

    public List<ModelSpec> byProvider(String providerName) {
        return models.stream().filter(m -> m.provider().equals(providerName)).toList();
    }

    /**
     * The single globally most-expensive configured model, by virtual price-per-1K-tokens. Used
     * to compute "baseline cost" (what a request would have cost if every request always used
     * the priciest model) in both {@code UsageLogService} (real traffic) and the Stage 9
     * benchmark harness's {@code ALWAYS_EXPENSIVE} strategy - kept in one place so both agree on
     * which model that is.
     */
    public ModelSpec mostExpensive() {
        return models.stream()
                .max(java.util.Comparator.comparingDouble(ModelSpec::pricePerKTokens))
                .orElseThrow(() -> new IllegalStateException("No models configured in models.yml"));
    }

    @SuppressWarnings("unchecked")
    static List<ModelSpec> loadFromClasspath(String resourceName) {
        Yaml yaml = new Yaml();
        try (InputStream in = new ClassPathResource(resourceName).getInputStream()) {
            Map<String, Object> root = yaml.load(in);
            Object rawModels = root == null ? null : root.get("models");
            if (!(rawModels instanceof List<?> rawList)) {
                throw new IllegalStateException(resourceName + " must contain a top-level 'models' list");
            }

            List<ModelSpec> parsed = new ArrayList<>();
            for (Object entry : rawList) {
                Map<String, Object> raw = (Map<String, Object>) entry;
                parsed.add(new ModelSpec(
                        (String) raw.get("provider"),
                        (String) raw.get("model"),
                        toDouble(raw.get("price-per-1k-tokens")),
                        toLong(raw.get("avg-latency-ms")),
                        QualityTier.valueOf(((String) raw.get("quality-tier")).toUpperCase())));
            }
            return parsed;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + resourceName + " from classpath", e);
        }
    }

    private static double toDouble(Object value) {
        return ((Number) value).doubleValue();
    }

    private static long toLong(Object value) {
        return ((Number) value).longValue();
    }
}
