package com.example.llmrouter.routing.strategy;

import com.example.llmrouter.routing.ModelSpec;
import com.example.llmrouter.routing.RoutingContext;
import com.example.llmrouter.routing.RoutingDecision;
import com.example.llmrouter.routing.RoutingStrategy;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** Picks the model with the highest {@link com.example.llmrouter.routing.QualityTier}; ties broken by lowest virtual price. */
@Component
public class QualityRoutingStrategy implements RoutingStrategy {

    @Override
    public List<ModelSpec> rank(RoutingContext context, List<ModelSpec> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparing(ModelSpec::qualityTier)
                        .thenComparing(Comparator.comparingDouble(ModelSpec::pricePerKTokens).reversed())
                        .reversed())
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
                "quality strategy: chose %s/%s at quality tier %s (highest among %d candidates)",
                chosen.provider(), chosen.model(), chosen.qualityTier(), candidates.size());
        return new RoutingDecision(chosen, reason);
    }
}
