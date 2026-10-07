package com.example.llmrouter.usage;

import com.example.llmrouter.apikey.ApiKey;
import com.example.llmrouter.chat.ChatResponse;
import com.example.llmrouter.chat.Priority;
import com.example.llmrouter.routing.ModelRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageLogServiceTest {

    @Mock
    private UsageLogRepository repository;

    // Real models.yml pricing: mock=0.0, groq=0.05, openrouter=0.0, gemini=0.02 per 1k tokens -
    // "most expensive" (the baseline) is groq at 0.05/1k.
    private final ModelRegistry modelRegistry = new ModelRegistry();

    // A real (in-memory, not mocked) registry so metrics assertions exercise the actual
    // Micrometer counters/timers, not a mock's recorded interactions.
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private UsageLogService usageLogService;

    @BeforeEach
    void setUp() {
        // Constructed here (not as a field initializer) because @Mock fields are injected by
        // MockitoExtension after instance construction but before @BeforeEach runs.
        usageLogService = new UsageLogService(repository, modelRegistry, meterRegistry);
    }

    private static ApiKey apiKeyWithId(long id) {
        ApiKey apiKey = mock(ApiKey.class);
        when(apiKey.id()).thenReturn(id);
        when(apiKey.clientName()).thenReturn("acme");
        return apiKey;
    }

    @Test
    void record_computesVirtualCostFromTheActualModelsPrice_onACacheMiss() {
        ApiKey apiKey = apiKeyWithId(1L);
        // gemini: 0.02/1k tokens, 2000 total tokens -> virtualCost = 0.04; baseline uses groq's 0.05/1k -> 0.10
        ChatResponse response = new ChatResponse(
                "hi", "gemini", "gemini-1.5-flash", 1000, 1000, 2000, 50, Priority.QUALITY, "reason", false, null, false);

        usageLogService.record(apiKey, Priority.QUALITY, response);

        ArgumentCaptor<UsageLog> captor = ArgumentCaptor.forClass(UsageLog.class);
        verify(repository).save(captor.capture());
        UsageLog saved = captor.getValue();
        assertThat(saved.virtualCost()).isCloseTo(0.04, within(1e-9));
        assertThat(saved.baselineCost()).isCloseTo(0.10, within(1e-9));
        assertThat(saved.apiKeyId()).isEqualTo(1L);
        assertThat(saved.clientName()).isEqualTo("acme");
        assertThat(saved.cacheHit()).isFalse();

        assertThat(meterRegistry.counter("llm.requests.total",
                "provider", "gemini", "model", "gemini-1.5-flash", "priority", "QUALITY",
                "cache_hit", "false", "fallback_used", "false").count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("llm.provider.latency")
                .tag("provider", "gemini").tag("model", "gemini-1.5-flash").timer().count()).isEqualTo(1);
        assertThat(meterRegistry.counter("llm.virtual.cost.total",
                "provider", "gemini", "model", "gemini-1.5-flash").count()).isCloseTo(0.04, within(1e-9));
        assertThat(meterRegistry.counter("llm.baseline.cost.total",
                "provider", "gemini", "model", "gemini-1.5-flash").count()).isCloseTo(0.10, within(1e-9));
    }

    @Test
    void record_forcesVirtualCostToZero_onACacheHit_butStillComputesBaselineCost() {
        ApiKey apiKey = apiKeyWithId(2L);
        // cache hit still reports the originally-answering model/tokens, but costs nothing
        ChatResponse response = new ChatResponse(
                "hi", "gemini", "gemini-1.5-flash", 1000, 1000, 2000, 0, Priority.QUALITY, "reason", false, null, true);

        usageLogService.record(apiKey, Priority.QUALITY, response);

        ArgumentCaptor<UsageLog> captor = ArgumentCaptor.forClass(UsageLog.class);
        verify(repository).save(captor.capture());
        UsageLog saved = captor.getValue();
        assertThat(saved.virtualCost()).isZero();
        assertThat(saved.baselineCost()).isCloseTo(0.10, within(1e-9)); // same hypothetical as a miss would have
        assertThat(saved.cacheHit()).isTrue();

        // a cache hit still counts as a request, but contributes no provider-latency sample
        assertThat(meterRegistry.counter("llm.requests.total",
                "provider", "gemini", "model", "gemini-1.5-flash", "priority", "QUALITY",
                "cache_hit", "true", "fallback_used", "false").count()).isEqualTo(1.0);
        assertThat(meterRegistry.find("llm.provider.latency")
                .tag("provider", "gemini").tag("model", "gemini-1.5-flash").timer()).isNull();
    }

    @Test
    void record_fallsBackToZeroPrice_whenTheRespondingModelIsNotInTheRegistry() {
        ApiKey apiKey = apiKeyWithId(3L);
        ChatResponse response = new ChatResponse(
                "hi", "unknown-provider", "unknown-model", 500, 500, 1000, 10, Priority.AUTO, "reason", false, null, false);

        usageLogService.record(apiKey, Priority.AUTO, response);

        ArgumentCaptor<UsageLog> captor = ArgumentCaptor.forClass(UsageLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().virtualCost()).isZero();
    }

    @Test
    void summaryForClient_computesHitRateSavingsAndSavingsPercent() {
        ApiKey apiKey = apiKeyWithId(5L);
        when(repository.summarizeByClient(5L)).thenReturn(
                Optional.of(new ClientUsageAggregate(5L, "acme", 4L, 1L, 6.0, 10.0)));

        ClientUsageSummary summary = usageLogService.summaryForClient(apiKey);

        assertThat(summary.totalRequests()).isEqualTo(4L);
        assertThat(summary.cacheHits()).isEqualTo(1L);
        assertThat(summary.cacheHitRatePercent()).isCloseTo(25.0, within(1e-9));
        assertThat(summary.totalSavings()).isCloseTo(4.0, within(1e-9));
        assertThat(summary.savingsPercent()).isCloseTo(40.0, within(1e-9));
    }

    @Test
    void summaryForClient_returnsAllZeroes_whenTheClientHasNoUsageYet() {
        ApiKey apiKey = apiKeyWithId(6L);
        when(repository.summarizeByClient(6L)).thenReturn(Optional.empty());

        ClientUsageSummary summary = usageLogService.summaryForClient(apiKey);

        assertThat(summary.totalRequests()).isZero();
        assertThat(summary.cacheHitRatePercent()).isZero();
        assertThat(summary.savingsPercent()).isZero();
    }

    @Test
    void summaryByModel_mapsRepositoryAggregatesDirectly() {
        when(repository.summarizeByModel()).thenReturn(List.of(
                new ModelUsageAggregate("mock", "mock-model-v1", 10L, 0.0, 120.0)));

        List<ModelUsageSummary> summaries = usageLogService.summaryByModel();

        assertThat(summaries).containsExactly(new ModelUsageSummary("mock", "mock-model-v1", 10L, 0.0, 120.0));
    }
}
