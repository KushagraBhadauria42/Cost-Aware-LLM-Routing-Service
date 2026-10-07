package com.example.llmrouter.routing.strategy;

import com.example.llmrouter.routing.ModelSpec;
import com.example.llmrouter.routing.QualityTier;
import com.example.llmrouter.routing.RoutingContext;
import com.example.llmrouter.routing.RoutingDecision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FastRoutingStrategyTest {

    private static final List<ModelSpec> CANDIDATES = List.of(
            new ModelSpec("mock", "mock-model-v1", 0.0, 150, QualityTier.MEDIUM),
            new ModelSpec("groq", "llama-3.1-8b-instant", 0.05, 400, QualityTier.MEDIUM),
            new ModelSpec("openrouter", "llama-3.1-8b-instruct:free", 0.0, 900, QualityTier.MEDIUM),
            new ModelSpec("gemini", "gemini-1.5-flash", 0.02, 600, QualityTier.HIGH));

    private final FastRoutingStrategy strategy = new FastRoutingStrategy();

    @Test
    void choosesLowestEstimatedLatency() {
        RoutingDecision decision = strategy.select(new RoutingContext("hi"), CANDIDATES);

        assertThat(decision.chosen().provider()).isEqualTo("mock");
        assertThat(decision.chosen().avgLatencyMs()).isEqualTo(150);
        assertThat(decision.reason()).contains("fast strategy").contains("150");
    }

    @Test
    void rank_ordersAllCandidatesBestToWorst_forFallbackChain() {
        List<ModelSpec> ranked = strategy.rank(new RoutingContext("hi"), CANDIDATES);

        assertThat(ranked).extracting(ModelSpec::provider)
                .containsExactly("mock", "groq", "gemini", "openrouter");
    }
}
