package com.example.llmrouter.routing.strategy;

import com.example.llmrouter.routing.RoutingContext;
import com.example.llmrouter.routing.RoutingDecision;
import com.example.llmrouter.routing.RoutingStrategy;
import com.example.llmrouter.routing.ModelSpec;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Heuristically delegates to one of the other three strategies based on how long/complex the
 * prompt looks, since we have no real way to measure "difficulty" without calling a model first:
 * <ul>
 *     <li>Short prompts (few words) are usually simple lookups/greetings -&gt; {@link FastRoutingStrategy}.</li>
 *     <li>Long or code-containing prompts likely need more reasoning -&gt; {@link QualityRoutingStrategy}.</li>
 *     <li>Everything in between -&gt; {@link CheapRoutingStrategy} (reasonable default middle ground).</li>
 * </ul>
 */
@Component
public class AutoRoutingStrategy implements RoutingStrategy {

    static final int FAST_WORD_THRESHOLD = 8;
    static final int QUALITY_WORD_THRESHOLD = 60;

    private final CheapRoutingStrategy cheap;
    private final FastRoutingStrategy fast;
    private final QualityRoutingStrategy quality;

    public AutoRoutingStrategy(CheapRoutingStrategy cheap, FastRoutingStrategy fast, QualityRoutingStrategy quality) {
        this.cheap = cheap;
        this.fast = fast;
        this.quality = quality;
    }

    @Override
    public RoutingDecision select(RoutingContext context, List<ModelSpec> candidates) {
        RoutingStrategy delegateStrategy = pickDelegate(context);
        RoutingDecision delegate = delegateStrategy.select(context, candidates);
        return new RoutingDecision(delegate.chosen(),
                "auto strategy: " + describeHeuristic(context) + " -> delegated to " + delegate.reason());
    }

    @Override
    public List<ModelSpec> rank(RoutingContext context, List<ModelSpec> candidates) {
        return pickDelegate(context).rank(context, candidates);
    }

    /**
     * Same heuristic used by both {@link #select} and {@link #rank}: decides which concrete
     * strategy to delegate to, kept in one place so the two never disagree.
     */
    private RoutingStrategy pickDelegate(RoutingContext context) {
        int wordCount = countWords(context.prompt());
        boolean hasCodeBlock = context.prompt() != null && context.prompt().contains("```");

        if (wordCount > QUALITY_WORD_THRESHOLD || hasCodeBlock) {
            return quality;
        }
        if (wordCount <= FAST_WORD_THRESHOLD) {
            return fast;
        }
        return cheap;
    }

    private String describeHeuristic(RoutingContext context) {
        int wordCount = countWords(context.prompt());
        boolean hasCodeBlock = context.prompt() != null && context.prompt().contains("```");

        if (hasCodeBlock) {
            return "prompt looks complex (contains a code block)";
        }
        if (wordCount > QUALITY_WORD_THRESHOLD) {
            return "prompt looks complex (" + wordCount + " words exceeds the complexity threshold)";
        }
        if (wordCount <= FAST_WORD_THRESHOLD) {
            return "prompt is short (" + wordCount + " words)";
        }
        return "prompt is moderate length (" + wordCount + " words)";
    }

    private int countWords(String text) {
        if (text == null) {
            return 0;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
    }
}
