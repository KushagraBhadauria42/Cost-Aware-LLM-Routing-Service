package com.example.llmrouter.routing;

import com.example.llmrouter.chat.Priority;
import com.example.llmrouter.routing.strategy.AutoRoutingStrategy;
import com.example.llmrouter.routing.strategy.CheapRoutingStrategy;
import com.example.llmrouter.routing.strategy.FastRoutingStrategy;
import com.example.llmrouter.routing.strategy.QualityRoutingStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingEngineTest {

    private static final List<ModelSpec> CANDIDATES = List.of(
            new ModelSpec("mock", "mock-model-v1", 0.0, 150, QualityTier.MEDIUM),
            new ModelSpec("gemini", "gemini-1.5-flash", 0.02, 600, QualityTier.HIGH));

    private final RoutingEngine engine = new RoutingEngine(
            new ModelRegistry(CANDIDATES),
            new CheapRoutingStrategy(),
            new FastRoutingStrategy(),
            new QualityRoutingStrategy(),
            new AutoRoutingStrategy(new CheapRoutingStrategy(), new FastRoutingStrategy(), new QualityRoutingStrategy()));

    @Test
    void qualityPriority_selectsTheQualityStrategy() {
        RoutingDecision decision = engine.route("hello", Priority.QUALITY);

        assertThat(decision.chosen().provider()).isEqualTo("gemini");
    }

    @Test
    void cheapPriority_selectsTheCheapStrategy() {
        RoutingDecision decision = engine.route("hello", Priority.CHEAP);

        assertThat(decision.chosen().provider()).isEqualTo("mock");
    }

    @Test
    void throwsIllegalStateException_whenNoModelsAreRegistered() {
        RoutingEngine emptyEngine = new RoutingEngine(
                new ModelRegistry(List.of()),
                new CheapRoutingStrategy(),
                new FastRoutingStrategy(),
                new QualityRoutingStrategy(),
                new AutoRoutingStrategy(new CheapRoutingStrategy(), new FastRoutingStrategy(), new QualityRoutingStrategy()));

        assertThatThrownBy(() -> emptyEngine.route("hello", Priority.FAST))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("models.yml");
    }

    @Test
    void rank_returnsAllCandidatesOrderedByThePriorityStrategy() {
        List<ModelSpec> ranked = engine.rank("hello", Priority.CHEAP);

        assertThat(ranked).extracting(ModelSpec::provider).containsExactly("mock", "gemini");
    }
}
