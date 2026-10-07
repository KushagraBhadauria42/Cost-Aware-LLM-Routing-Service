package com.example.llmrouter.benchmark;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkPromptLoaderTest {

    @Test
    void prompts_loadsTheFixedBenchmarkSet_withinTheRequestedSizeRange() {
        List<String> prompts = new BenchmarkPromptLoader().prompts();

        assertThat(prompts).hasSizeBetween(50, 100);
        assertThat(prompts).allSatisfy(p -> assertThat(p).isNotBlank());
        assertThat(prompts).noneMatch(p -> p.startsWith("#"));
    }
}
