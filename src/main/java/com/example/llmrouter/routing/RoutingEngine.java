package com.example.llmrouter.routing;

import com.example.llmrouter.chat.Priority;
import com.example.llmrouter.routing.strategy.AutoRoutingStrategy;
import com.example.llmrouter.routing.strategy.CheapRoutingStrategy;
import com.example.llmrouter.routing.strategy.FastRoutingStrategy;
import com.example.llmrouter.routing.strategy.QualityRoutingStrategy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Entry point for routing: looks up the registered models, picks the {@link RoutingStrategy}
 * matching the request's {@link Priority}, and returns which model was chosen and why.
 */
@Service
public class RoutingEngine {

    private final ModelRegistry modelRegistry;
    private final Map<Priority, RoutingStrategy> strategiesByPriority;

    public RoutingEngine(
            ModelRegistry modelRegistry,
            CheapRoutingStrategy cheap,
            FastRoutingStrategy fast,
            QualityRoutingStrategy quality,
            AutoRoutingStrategy auto) {
        this.modelRegistry = modelRegistry;
        this.strategiesByPriority = Map.of(
                Priority.CHEAP, cheap,
                Priority.FAST, fast,
                Priority.QUALITY, quality,
                Priority.AUTO, auto);
    }

    public RoutingDecision route(String prompt, Priority priority) {
        List<ModelSpec> candidates = modelRegistry.all();
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No models configured in models.yml");
        }

        RoutingStrategy strategy = strategiesByPriority.get(priority);
        return strategy.select(new RoutingContext(prompt), candidates);
    }

    /**
     * Returns every registered model ordered best-to-worst per the strategy matching
     * {@code priority}. Used by {@code ChatService} to build a fallback chain: if the top
     * candidate's provider fails, the next-best candidate is tried.
     */
    public List<ModelSpec> rank(String prompt, Priority priority) {
        List<ModelSpec> candidates = modelRegistry.all();
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No models configured in models.yml");
        }

        RoutingStrategy strategy = strategiesByPriority.get(priority);
        return strategy.rank(new RoutingContext(prompt), candidates);
    }
}
