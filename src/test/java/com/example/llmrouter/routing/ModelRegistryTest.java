package com.example.llmrouter.routing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRegistryTest {

    @Test
    void loadsAllModelsFromClasspathYaml() {
        ModelRegistry registry = new ModelRegistry();

        List<ModelSpec> models = registry.all();

        assertThat(models).isNotEmpty();
        assertThat(models).extracting(ModelSpec::provider)
                .contains("mock", "groq", "openrouter", "gemini");
    }

    @Test
    void byProviderFiltersToOnlyThatProvider() {
        ModelRegistry registry = new ModelRegistry();

        List<ModelSpec> mockModels = registry.byProvider("mock");

        assertThat(mockModels).isNotEmpty();
        assertThat(mockModels).allMatch(m -> m.provider().equals("mock"));
    }
}
