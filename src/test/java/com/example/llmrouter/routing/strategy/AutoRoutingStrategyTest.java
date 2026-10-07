package com.example.llmrouter.routing.strategy;

import com.example.llmrouter.routing.ModelSpec;
import com.example.llmrouter.routing.QualityTier;
import com.example.llmrouter.routing.RoutingContext;
import com.example.llmrouter.routing.RoutingDecision;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AutoRoutingStrategyTest {

    private static final List<ModelSpec> CANDIDATES = List.of(
            new ModelSpec("mock", "mock-model-v1", 0.0, 150, QualityTier.MEDIUM),
            new ModelSpec("groq", "llama-3.1-8b-instant", 0.05, 400, QualityTier.MEDIUM),
            new ModelSpec("openrouter", "llama-3.1-8b-instruct:free", 0.0, 900, QualityTier.MEDIUM),
            new ModelSpec("gemini", "gemini-1.5-flash", 0.02, 600, QualityTier.HIGH));

    private final AutoRoutingStrategy strategy = new AutoRoutingStrategy(
            new CheapRoutingStrategy(), new FastRoutingStrategy(), new QualityRoutingStrategy());

    @Test
    void shortPrompt_delegatesToFastStrategy() {
        RoutingDecision decision = strategy.select(new RoutingContext("hi there"), CANDIDATES);

        assertThat(decision.chosen().provider()).isEqualTo("mock"); // fastest candidate
        assertThat(decision.reason()).contains("auto strategy").contains("short").contains("fast strategy");
    }

    @Test
    void longPrompt_delegatesToQualityStrategy() {
        String longPrompt = Stream.generate(() -> "word").limit(70).collect(Collectors.joining(" "));

        RoutingDecision decision = strategy.select(new RoutingContext(longPrompt), CANDIDATES);

        assertThat(decision.chosen().provider()).isEqualTo("gemini"); // highest quality tier
        assertThat(decision.reason()).contains("auto strategy").contains("complex").contains("quality strategy");
    }

    @Test
    void promptWithCodeBlock_delegatesToQualityStrategy_evenIfShort() {
        RoutingDecision decision = strategy.select(new RoutingContext("fix this:\n```\ncode\n```"), CANDIDATES);

        assertThat(decision.chosen().provider()).isEqualTo("gemini");
        assertThat(decision.reason()).contains("code block");
    }

    @Test
    void moderateLengthPrompt_delegatesToCheapStrategy() {
        String moderatePrompt = Stream.generate(() -> "word").limit(20).collect(Collectors.joining(" "));

        RoutingDecision decision = strategy.select(new RoutingContext(moderatePrompt), CANDIDATES);

        assertThat(decision.chosen().provider()).isEqualTo("mock"); // cheapest (tie-break by latency)
        assertThat(decision.reason()).contains("auto strategy").contains("moderate").contains("cheap strategy");
    }

    @Test
    void rank_delegatesFullOrderingToTheChosenStrategy() {
        List<ModelSpec> ranked = strategy.rank(new RoutingContext("hi there"), CANDIDATES); // short -> fast

        assertThat(ranked).extracting(ModelSpec::provider)
                .containsExactly("mock", "groq", "gemini", "openrouter");
    }
}
