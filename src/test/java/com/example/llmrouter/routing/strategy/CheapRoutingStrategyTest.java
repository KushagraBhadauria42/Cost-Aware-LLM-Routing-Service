package com.example.llmrouter.routing.strategy;

import com.example.llmrouter.routing.ModelSpec;
import com.example.llmrouter.routing.QualityTier;
import com.example.llmrouter.routing.RoutingContext;
import com.example.llmrouter.routing.RoutingDecision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CheapRoutingStrategyTest {

    private static final List<ModelSpec> CANDIDATES = List.of(
            new ModelSpec("mock", "mock-model-v1", 0.0, 150, QualityTier.MEDIUM),
            new ModelSpec("groq", "llama-3.1-8b-instant", 0.05, 400, QualityTier.MEDIUM),
            new ModelSpec("openrouter", "llama-3.1-8b-instruct:free", 0.0, 900, QualityTier.MEDIUM),
            new ModelSpec("gemini", "gemini-1.5-flash", 0.02, 600, QualityTier.HIGH));

    private final CheapRoutingStrategy strategy = new CheapRoutingStrategy();

    @Test
    void choosesLowestPrice_breakingTiesByLowestLatency() {
        // mock and openrouter are both free (0.0); mock has lower latency (150 vs 900) so it wins.
        RoutingDecision decision = strategy.select(new RoutingContext("hi"), CANDIDATES);

        assertThat(decision.chosen().provider()).isEqualTo("mock");
        assertThat(decision.reason()).contains("cheap strategy").contains("mock");
    }

    @Test
    void rank_ordersAllCandidatesBestToWorst_forFallbackChain() {
        List<ModelSpec> ranked = strategy.rank(new RoutingContext("hi"), CANDIDATES);

        assertThat(ranked).extracting(ModelSpec::provider)
                .containsExactly("mock", "openrouter", "gemini", "groq");
    }
}
