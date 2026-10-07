package com.example.llmrouter.usage;

import com.example.llmrouter.apikey.ApiKey;
import com.example.llmrouter.chat.ChatResponse;
import com.example.llmrouter.chat.Priority;
import com.example.llmrouter.routing.ModelRegistry;
import com.example.llmrouter.routing.ModelSpec;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

/**
 * Records one {@link UsageLog} row per {@code /v1/chat} response (see {@link UsageLog}'s javadoc
 * for why cache hits get a row too), and builds the derived summaries {@code /v1/usage} returns.
 * <p>
 * "Virtual cost" and "baseline cost" are both computed from {@link ModelRegistry}'s made-up
 * per-model prices (see Stage 3) — baseline cost always uses whatever model in {@code models.yml}
 * has the highest {@code pricePerKTokens}, representing "what this request would have cost if
 * every request always used the most expensive model", so the gap between the two is this
 * project's "savings" story. Baseline cost is computed even for cache hits (same token count,
 * same hypothetical most-expensive-model price) since it answers a hypothetical question, not an
 * actual one; virtual cost is {@code 0} for cache hits because Stage 5 made that true in fact.
 * <p>
 * Stage 8: Micrometer metrics are recorded in this exact same method, from the exact same
 * {@code virtualCost}/{@code baselineCost}/{@code response} values that get persisted below —
 * deliberately NOT a separate independent recording path (e.g. instrumenting
 * {@code ChatService} or each {@code LlmProvider} separately), so the Prometheus counters and the
 * {@code usage_log} table can never drift apart from double-counting, missed edge cases, or two
 * copies of the cost formula silently diverging over time. Metrics are intentionally NOT tagged
 * by client/API key — that's an unbounded-cardinality dimension that belongs in the
 * {@code usage_log} table (queried per-client via {@code /v1/usage}), not in a Prometheus label.
 */
@Service
public class UsageLogService {

    private final UsageLogRepository repository;
    private final ModelRegistry modelRegistry;
    private final MeterRegistry meterRegistry;

    public UsageLogService(UsageLogRepository repository, ModelRegistry modelRegistry, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.modelRegistry = modelRegistry;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public void record(ApiKey apiKey, Priority priority, ChatResponse response) {
        double virtualCost = response.cacheHit()
                ? 0.0
                : CostCalculator.cost(response.totalTokens(), pricePerKTokensFor(response.provider(), response.model()));
        double baselineCost = CostCalculator.cost(response.totalTokens(), modelRegistry.mostExpensive().pricePerKTokens());

        UsageLog log = new UsageLog(
                apiKey.id(),
                apiKey.clientName(),
                priority,
                response.provider(),
                response.model(),
                response.promptTokens(),
                response.completionTokens(),
                response.totalTokens(),
                response.latencyMs(),
                virtualCost,
                baselineCost,
                response.cacheHit(),
                response.fallbackUsed());
        repository.save(log);
        recordMetrics(priority, response, virtualCost, baselineCost);
    }

    private void recordMetrics(Priority priority, ChatResponse response, double virtualCost, double baselineCost) {
        String provider = response.provider();
        String model = response.model();
        String cacheHit = String.valueOf(response.cacheHit());
        String fallbackUsed = String.valueOf(response.fallbackUsed());

        meterRegistry.counter("llm.requests.total",
                        "provider", provider, "model", model, "priority", priority.name(),
                        "cache_hit", cacheHit, "fallback_used", fallbackUsed)
                .increment();

        // Only real provider calls have a meaningful latency; a cache hit's latencyMs is always 0
        // by construction (Stage 5), and would falsely drag down the per-provider latency numbers.
        if (!response.cacheHit()) {
            Timer.builder("llm.provider.latency")
                    .tag("provider", provider)
                    .tag("model", model)
                    .register(meterRegistry)
                    .record(Duration.ofMillis(response.latencyMs()));
        }

        meterRegistry.counter("llm.virtual.cost.total", "provider", provider, "model", model)
                .increment(virtualCost);
        meterRegistry.counter("llm.baseline.cost.total", "provider", provider, "model", model)
                .increment(baselineCost);
    }


    public ClientUsageSummary summaryForClient(ApiKey apiKey) {
        ClientUsageAggregate aggregate = repository.summarizeByClient(apiKey.id())
                .orElse(new ClientUsageAggregate(apiKey.id(), apiKey.clientName(), 0L, 0L, 0.0, 0.0));

        double totalSavings = aggregate.totalBaselineCost() - aggregate.totalVirtualCost();
        double savingsPercent = aggregate.totalBaselineCost() == 0.0
                ? 0.0
                : (totalSavings / aggregate.totalBaselineCost()) * 100.0;
        double cacheHitRatePercent = aggregate.totalRequests() == 0
                ? 0.0
                : (aggregate.cacheHits() * 100.0) / aggregate.totalRequests();

        return new ClientUsageSummary(
                aggregate.apiKeyId(),
                aggregate.clientName(),
                aggregate.totalRequests(),
                aggregate.cacheHits(),
                cacheHitRatePercent,
                aggregate.totalVirtualCost(),
                aggregate.totalBaselineCost(),
                totalSavings,
                savingsPercent);
    }

    public List<ModelUsageSummary> summaryByModel() {
        return repository.summarizeByModel().stream()
                .map(a -> new ModelUsageSummary(a.provider(), a.model(), a.totalRequests(), a.totalVirtualCost(),
                        a.avgLatencyMs()))
                .toList();
    }

    private double pricePerKTokensFor(String provider, String model) {
        return modelRegistry.all().stream()
                .filter(m -> m.provider().equals(provider) && m.model().equals(model))
                .mapToDouble(ModelSpec::pricePerKTokens)
                .findFirst()
                .orElse(0.0);
    }

}
