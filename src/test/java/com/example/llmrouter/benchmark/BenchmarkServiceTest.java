package com.example.llmrouter.benchmark;

import com.example.llmrouter.provider.LlmProvider;
import com.example.llmrouter.provider.ProviderResult;
import com.example.llmrouter.provider.mock.MockProvider;
import com.example.llmrouter.provider.mock.MockProviderProperties;
import com.example.llmrouter.routing.ModelRegistry;
import com.example.llmrouter.routing.RoutingEngine;
import com.example.llmrouter.routing.strategy.AutoRoutingStrategy;
import com.example.llmrouter.routing.strategy.CheapRoutingStrategy;
import com.example.llmrouter.routing.strategy.FastRoutingStrategy;
import com.example.llmrouter.routing.strategy.QualityRoutingStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class BenchmarkServiceTest {

    // Real models.yml pricing: mock=0.0 (lowest price AND lowest latency), groq=0.05 (highest
    // price - the ALWAYS_EXPENSIVE/baseline model), openrouter=0.0, gemini=0.02 (sole HIGH quality
    // tier, so QUALITY always picks it).
    private final ModelRegistry modelRegistry = new ModelRegistry();
    private final CheapRoutingStrategy cheap = new CheapRoutingStrategy();
    private final FastRoutingStrategy fast = new FastRoutingStrategy();
    private final QualityRoutingStrategy quality = new QualityRoutingStrategy();
    private final AutoRoutingStrategy auto = new AutoRoutingStrategy(cheap, fast, quality);
    private final RoutingEngine routingEngine = new RoutingEngine(modelRegistry, cheap, fast, quality, auto);
    private final MockProvider mockProvider = new MockProvider(fastMockProperties());

    private static MockProviderProperties fastMockProperties() {
        // Near-zero simulated latency: this test only cares about aggregation/cost correctness,
        // not about realistically reproducing MockProvider's default 50-300ms sleep - running the
        // full ~57-prompt set under every strategy at the default latency would make this single
        // test class take well over a minute for no benefit.
        MockProviderProperties properties = new MockProviderProperties();
        properties.setMinLatencyMs(0);
        properties.setMaxLatencyMs(1);
        return properties;
    }

    private BenchmarkService serviceWithProviders(Map<String, LlmProvider> extraProviders) {
        List<LlmProvider> providers = new java.util.ArrayList<>(extraProviders.values());
        providers.add(mockProvider);
        return new BenchmarkService(new BenchmarkPromptLoader(), routingEngine, modelRegistry, mockProvider, providers);
    }

    private BenchmarkService defaultService() {
        return serviceWithProviders(Map.of());
    }

    @Test
    void run_coversEveryPromptInTheFixedSet() {
        BenchmarkReport report = defaultService().run(List.of(BenchmarkStrategy.CHEAP), false);

        assertThat(report.promptCount()).isBetween(50, 100);
        assertThat(report.strategyReports()).hasSize(1);
        assertThat(report.strategyReports().get(0).promptCount()).isEqualTo(report.promptCount());
    }

    @Test
    void run_alwaysExpensiveStrategy_hasZeroSavings_sinceItAlwaysPicksTheBaselineModelItself() {
        BenchmarkReport report = defaultService().run(List.of(BenchmarkStrategy.ALWAYS_EXPENSIVE), false);
        BenchmarkStrategyReport r = report.strategyReports().get(0);

        // ALWAYS_EXPENSIVE forces the same model used to compute "baseline cost" -> virtual cost
        // must exactly equal baseline cost every time -> 0% savings.
        assertThat(r.totalVirtualCost()).isEqualTo(r.totalBaselineCost(), offset(1e-9));
        assertThat(r.savingsPercent()).isEqualTo(0.0, offset(1e-9));
        assertThat(r.totalVirtualCost()).isGreaterThan(0.0);
    }

    @Test
    void run_cheapAndFastStrategies_alwaysPickTheFreeModel_soSavingsIs100Percent() {
        BenchmarkReport report = defaultService().run(List.of(BenchmarkStrategy.CHEAP, BenchmarkStrategy.FAST), false);

        for (BenchmarkStrategyReport r : report.strategyReports()) {
            assertThat(r.totalVirtualCost()).isEqualTo(0.0, offset(1e-9));
            assertThat(r.savingsPercent()).isEqualTo(100.0, offset(1e-9));
        }
    }

    @Test
    void run_qualityStrategy_picksTheSoleHighTierModel_withSavingsBetweenZeroAndHundred() {
        BenchmarkReport report = defaultService().run(List.of(BenchmarkStrategy.QUALITY), false);
        BenchmarkStrategyReport r = report.strategyReports().get(0);

        assertThat(r.totalVirtualCost()).isGreaterThan(0.0);
        assertThat(r.totalVirtualCost()).isLessThan(r.totalBaselineCost());
        assertThat(r.savingsPercent()).isBetween(0.0, 100.0);
    }

    @Test
    void run_autoStrategy_mixesModels_withLatencyPercentilesOrderedCorrectly() {
        BenchmarkReport report = defaultService().run(List.of(BenchmarkStrategy.AUTO), false);
        BenchmarkStrategyReport r = report.strategyReports().get(0);

        assertThat(r.savingsPercent()).isBetween(0.0, 100.0);
        assertThat(r.p50LatencyMs()).isLessThanOrEqualTo(r.p95LatencyMs());
        assertThat(r.avgLatencyMs()).isGreaterThan(0.0);
    }

    @Test
    void run_defaultMode_neverMakesARealNetworkCall_evenForNonMockStrategies() {
        BenchmarkReport report = defaultService()
                .run(List.of(BenchmarkStrategy.ALWAYS_EXPENSIVE, BenchmarkStrategy.QUALITY, BenchmarkStrategy.AUTO), false);

        assertThat(report.usedRealProviders()).isFalse();
        for (BenchmarkStrategyReport r : report.strategyReports()) {
            assertThat(r.realNetworkCalls()).isZero();
        }
    }

    @Test
    void run_realProviders_withNoMatchingProviderBeanRegistered_fallsBackToSimulatedPath() {
        // useRealProviders=true but no "gemini"/"groq" LlmProvider bean exists in the providers
        // list (only mock) -> every non-mock candidate must gracefully fall back to simulation,
        // not throw.
        BenchmarkReport report = defaultService().run(List.of(BenchmarkStrategy.QUALITY), true);
        BenchmarkStrategyReport r = report.strategyReports().get(0);

        assertThat(r.realNetworkCalls()).isZero();
    }

    @Test
    void run_realProviders_withAStubProviderRegistered_actuallyCallsIt() {
        LlmProvider stubGemini = new LlmProvider() {
            @Override
            public String name() {
                return "gemini";
            }

            @Override
            public ProviderResult complete(String prompt) {
                return new ProviderResult("stub response", "gemini-1.5-flash", 10, 5, 77);
            }
        };

        BenchmarkReport report = serviceWithProviders(Map.of("gemini", stubGemini))
                .run(List.of(BenchmarkStrategy.QUALITY), true);
        BenchmarkStrategyReport r = report.strategyReports().get(0);

        // QUALITY always picks gemini (sole HIGH tier) -> every one of the prompts should have
        // gone through the real stub, not the simulated path.
        assertThat(r.realNetworkCalls()).isEqualTo(r.promptCount());
    }
}
