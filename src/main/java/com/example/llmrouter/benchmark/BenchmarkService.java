package com.example.llmrouter.benchmark;

import com.example.llmrouter.chat.Priority;
import com.example.llmrouter.provider.LlmProvider;
import com.example.llmrouter.provider.ProviderException;
import com.example.llmrouter.provider.ProviderResult;
import com.example.llmrouter.provider.mock.MockProvider;
import com.example.llmrouter.routing.ModelRegistry;
import com.example.llmrouter.routing.ModelSpec;
import com.example.llmrouter.routing.RoutingEngine;
import com.example.llmrouter.usage.CostCalculator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Replays the fixed Stage 9 prompt set ({@link BenchmarkPromptLoader}) under every requested
 * {@link BenchmarkStrategy} and reports virtual cost, savings percentage, and latency (p50/p95).
 * <p>
 * <b>Deliberately does NOT touch {@code usage_log} or Micrometer metrics at all</b> - it never
 * calls {@code UsageLogService} or {@code ChatCacheService}. This is a design decision, not an
 * oversight: benchmark traffic is synthetic (a fixed, repeated prompt set replayed under every
 * strategy, potentially many times a day during development), and if it flowed through the same
 * recording path as real {@code /v1/chat} traffic it would inflate every client's/global usage
 * numbers in {@code /v1/usage} and pollute the live Grafana dashboard with fake request volume,
 * fake cache-hit rates, and fake cost figures that have nothing to do with real customers. Instead,
 * cost is computed by calling the exact same shared {@link CostCalculator}/{@link ModelRegistry}
 * formula that {@code UsageLogService} uses for real traffic, so the *numbers* can't drift from
 * what production would compute for the same tokens/model - only the *recording* (DB row, counter
 * increment) is skipped.
 * <p>
 * By default ({@code useRealProviders=false}) no real network call is ever made, regardless of
 * which strategy/model routing would have picked: token counts come from a free, local
 * {@link MockProvider#complete(String)} call (token estimation only depends on prompt/response
 * text length, not which provider "answers", so re-using mock's estimate for every strategy is a
 * reasonable, zero-cost stand-in), and latency for any non-mock candidate is simulated by jittering
 * {@code models.yml}'s {@code avg-latency-ms} field - which that field's own javadoc already
 * documents as "virtual... made-up planning numbers... not real SLA data", i.e. it was already
 * designed for exactly this kind of simulation. This keeps the benchmark entirely free and
 * rate-limit-safe to run as often as desired. Setting {@code useRealProviders=true} opts into
 * actually calling each strategy's chosen non-mock provider for real (consuming real quota); any
 * real-call failure (e.g. no API key configured) gracefully falls back to the simulated path for
 * that one sample rather than failing the whole run.
 */
@Service
public class BenchmarkService {

    /** Fixed seed: simulated latencies are reproducible across runs, not just "random each time". */
    private static final long JITTER_SEED = 42L;
    private static final double JITTER_LOWER_BOUND = 0.85;
    private static final double JITTER_RANGE = 0.30;

    private final BenchmarkPromptLoader promptLoader;
    private final RoutingEngine routingEngine;
    private final ModelRegistry modelRegistry;
    private final MockProvider mockProvider;
    private final Map<String, LlmProvider> providersByName;

    public BenchmarkService(
            BenchmarkPromptLoader promptLoader,
            RoutingEngine routingEngine,
            ModelRegistry modelRegistry,
            MockProvider mockProvider,
            List<LlmProvider> providers) {
        this.promptLoader = promptLoader;
        this.routingEngine = routingEngine;
        this.modelRegistry = modelRegistry;
        this.mockProvider = mockProvider;
        this.providersByName = providers.stream().collect(Collectors.toMap(LlmProvider::name, Function.identity()));
    }

    public BenchmarkReport run(List<BenchmarkStrategy> strategies, boolean useRealProviders) {
        List<String> prompts = promptLoader.prompts();
        double mostExpensivePrice = modelRegistry.mostExpensive().pricePerKTokens();
        Random jitterRandom = new Random(JITTER_SEED);

        List<BenchmarkStrategyReport> reports = new ArrayList<>();
        for (BenchmarkStrategy strategy : strategies) {
            List<BenchmarkSample> samples = new ArrayList<>(prompts.size());
            for (String prompt : prompts) {
                samples.add(runOne(strategy, prompt, mostExpensivePrice, useRealProviders, jitterRandom));
            }
            reports.add(aggregate(strategy, samples));
        }
        return new BenchmarkReport(prompts.size(), useRealProviders, reports);
    }

    private BenchmarkSample runOne(
            BenchmarkStrategy strategy,
            String prompt,
            double mostExpensivePrice,
            boolean useRealProviders,
            Random jitterRandom) {
        ModelSpec chosen = chooseModel(strategy, prompt);
        boolean isMockCandidate = chosen.provider().equals(mockProvider.name());

        if (useRealProviders && !isMockCandidate) {
            LlmProvider real = providersByName.get(chosen.provider());
            if (real != null) {
                try {
                    ProviderResult result = real.complete(prompt);
                    return toSample(strategy, chosen, result.totalTokens(), result.latencyMs(), true, mostExpensivePrice);
                } catch (ProviderException e) {
                    // Real call failed (e.g. no API key / rate limited) - fall through to the free,
                    // simulated path below instead of failing the whole benchmark run.
                }
            }
        }

        // Always run the free, local MockProvider for a zero-cost token estimate - never a real
        // network call, regardless of which provider was actually "chosen" by routing.
        ProviderResult mockResult = mockProvider.complete(prompt);
        long latencyMs = isMockCandidate
                ? mockResult.latencyMs()
                : simulateLatency(chosen.avgLatencyMs(), jitterRandom);
        return toSample(strategy, chosen, mockResult.totalTokens(), latencyMs, false, mostExpensivePrice);
    }

    private static BenchmarkSample toSample(
            BenchmarkStrategy strategy,
            ModelSpec chosen,
            int totalTokens,
            long latencyMs,
            boolean usedRealNetworkCall,
            double mostExpensivePrice) {
        double virtualCost = CostCalculator.cost(totalTokens, chosen.pricePerKTokens());
        double baselineCost = CostCalculator.cost(totalTokens, mostExpensivePrice);
        return new BenchmarkSample(
                strategy, chosen.provider(), chosen.model(), totalTokens, latencyMs, usedRealNetworkCall,
                virtualCost, baselineCost);
    }

    private ModelSpec chooseModel(BenchmarkStrategy strategy, String prompt) {
        if (strategy == BenchmarkStrategy.ALWAYS_EXPENSIVE) {
            return modelRegistry.mostExpensive();
        }
        Priority priority = Priority.valueOf(strategy.name());
        return routingEngine.route(prompt, priority).chosen();
    }

    private static long simulateLatency(long avgLatencyMs, Random random) {
        double jitterFactor = JITTER_LOWER_BOUND + (random.nextDouble() * JITTER_RANGE);
        return Math.max(1, Math.round(avgLatencyMs * jitterFactor));
    }

    private static BenchmarkStrategyReport aggregate(BenchmarkStrategy strategy, List<BenchmarkSample> samples) {
        double totalVirtualCost = samples.stream().mapToDouble(BenchmarkSample::virtualCost).sum();
        double totalBaselineCost = samples.stream().mapToDouble(BenchmarkSample::baselineCost).sum();
        double savingsPercent = totalBaselineCost == 0.0
                ? 0.0
                : ((totalBaselineCost - totalVirtualCost) / totalBaselineCost) * 100.0;

        long[] sortedLatencies = samples.stream().mapToLong(BenchmarkSample::latencyMs).sorted().toArray();
        double avgLatencyMs = Arrays.stream(sortedLatencies).average().orElse(0.0);
        double p50LatencyMs = percentile(sortedLatencies, 50);
        double p95LatencyMs = percentile(sortedLatencies, 95);
        long realNetworkCalls = samples.stream().filter(BenchmarkSample::usedRealNetworkCall).count();

        return new BenchmarkStrategyReport(
                strategy, samples.size(), totalVirtualCost, totalBaselineCost, savingsPercent,
                avgLatencyMs, p50LatencyMs, p95LatencyMs, realNetworkCalls);
    }

    private static double percentile(long[] sortedValues, double targetPercentile) {
        if (sortedValues.length == 0) {
            return 0.0;
        }
        int index = (int) Math.ceil((targetPercentile / 100.0) * sortedValues.length) - 1;
        index = Math.max(0, Math.min(sortedValues.length - 1, index));
        return sortedValues[index];
    }
}
