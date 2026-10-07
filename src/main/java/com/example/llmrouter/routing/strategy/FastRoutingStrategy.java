package com.example.llmrouter.routing.strategy;

import com.example.llmrouter.routing.ModelSpec;
import com.example.llmrouter.routing.RoutingContext;
import com.example.llmrouter.routing.RoutingDecision;
import com.example.llmrouter.routing.RoutingStrategy;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** Picks the model with the lowest estimated latency; ties broken by lowest virtual price. */
@Component
public class FastRoutingStrategy implements RoutingStrategy {

    @Override
    public List<ModelSpec> rank(RoutingContext context, List<ModelSpec> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparingLong(ModelSpec::avgLatencyMs)
                        .thenComparingDouble(ModelSpec::pricePerKTokens))
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
                "fast strategy: chose %s/%s with an estimated %dms latency (lowest among %d candidates)",
                chosen.provider(), chosen.model(), chosen.avgLatencyMs(), candidates.size());
        return new RoutingDecision(chosen, reason);
    }
}
