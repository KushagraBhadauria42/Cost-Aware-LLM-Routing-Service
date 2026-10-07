package com.example.llmrouter.usage;

import java.util.List;

/** Response body for {@code GET /v1/usage}. */
public record UsageSummaryResponse(ClientUsageSummary client, List<ModelUsageSummary> byModel) {
}
