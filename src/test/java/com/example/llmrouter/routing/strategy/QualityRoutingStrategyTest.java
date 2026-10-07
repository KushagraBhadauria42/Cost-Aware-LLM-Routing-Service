package com.example.llmrouter.routing.strategy;

import com.example.llmrouter.routing.ModelSpec;
import com.example.llmrouter.routing.QualityTier;
import com.example.llmrouter.routing.RoutingContext;
import com.example.llmrouter.routing.RoutingDecision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QualityRoutingStrategyTest {

    private static final List<ModelSpec> CANDIDATES = List.of(
            new ModelSpec("mock", "mock-model-v1", 0.0, 150, QualityTier.MEDIUM),
            new ModelSpec("groq", "llama-3.1-8b-instant", 0.05, 400, QualityTier.MEDIUM),
            new ModelSpec("openrouter", "llama-3.1-8b-instruct:free", 0.0, 900, QualityTier.MEDIUM),
            new ModelSpec("gemini", "gemini-1.5-flash", 0.02, 600, QualityTier.HIGH));

    private final QualityRoutingStrategy strategy = new QualityRoutingStrategy();

    @Test
    void choosesHighestQualityTier_regardlessOfPrice() {
        RoutingDecision decision = strategy.select(new RoutingContext("hi"), CANDIDATES);

        assertThat(decision.chosen().provider()).isEqualTo("gemini");
        assertThat(decision.chosen().qualityTier()).isEqualTo(QualityTier.HIGH);
        assertThat(decision.reason()).contains("quality strategy").contains("HIGH");
    }

    @Test
    void tiesAreBrokenByLowestPrice() {
        List<ModelSpec> allMediumExceptPriceDiffers = List.of(
                new ModelSpec("a", "model-a", 0.10, 100, QualityTier.MEDIUM),
                new ModelSpec("b", "model-b", 0.01, 100, QualityTier.MEDIUM));

        RoutingDecision decision = strategy.select(new RoutingContext("hi"), allMediumExceptPriceDiffers);

        assertThat(decision.chosen().provider()).isEqualTo("b");
    }

    @Test
    void rank_ordersAllCandidatesBestToWorst_forFallbackChain() {
        List<ModelSpec> ranked = strategy.rank(new RoutingContext("hi"), CANDIDATES);

        assertThat(ranked).extracting(ModelSpec::provider)
                .containsExactly("gemini", "mock", "openrouter", "groq");
    }
}
