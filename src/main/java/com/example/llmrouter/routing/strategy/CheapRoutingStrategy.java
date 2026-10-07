package com.example.llmrouter.routing.strategy;

import com.example.llmrouter.routing.ModelSpec;
import com.example.llmrouter.routing.RoutingContext;
import com.example.llmrouter.routing.RoutingDecision;
import com.example.llmrouter.routing.RoutingStrategy;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** Picks the model with the lowest virtual price per 1K tokens; ties broken by lowest latency. */
@Component
public class CheapRoutingStrategy implements RoutingStrategy {

    @Override
    public List<ModelSpec> rank(RoutingContext context, List<ModelSpec> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparingDouble(ModelSpec::pricePerKTokens)
                        .thenComparingLong(ModelSpec::avgLatencyMs))
                .toList();
    }

    @Override
    public RoutingDecision select(RoutingContext context, List<ModelSpec> candidates) {
        List<ModelSpec> ranked = rank(context, candidates);
        if (ranked.isEmpty()) {
            throw new IllegalStateException("No models available to route to");
        }
        ModelSpec chosen = ranked.get(0);

        String reason = String.format(
                "cheap strategy: chose %s/%s at $%.4f per 1K tokens (lowest virtual cost among %d candidates)",
                chosen.provider(), chosen.model(), chosen.pricePerKTokens(), candidates.size());
        return new RoutingDecision(chosen, reason);
    }
}
