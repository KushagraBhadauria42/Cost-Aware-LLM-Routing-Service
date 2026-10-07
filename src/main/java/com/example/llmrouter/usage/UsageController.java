package com.example.llmrouter.usage;

import com.example.llmrouter.apikey.ApiKey;
import com.example.llmrouter.apikey.ApiKeyAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reports usage/cost summaries. Deliberately scoped to the authenticated caller's own client for
 * the per-client half of the summary — exposing every client's aggregate spend to any single
 * valid API key would be a broken-access-control issue (one tenant could see another tenant's
 * cost data). Per-model numbers are global since model pricing isn't tenant-specific information.
 */
@RestController
@RequestMapping("/v1/usage")
public class UsageController {

    private final UsageLogService usageLogService;

    public UsageController(UsageLogService usageLogService) {
        this.usageLogService = usageLogService;
    }

    @GetMapping
    public ResponseEntity<UsageSummaryResponse> usage(HttpServletRequest request) {
        ApiKey apiKey = (ApiKey) request.getAttribute(ApiKeyAuthFilter.AUTHENTICATED_API_KEY_ATTRIBUTE);
        UsageSummaryResponse body = new UsageSummaryResponse(
                usageLogService.summaryForClient(apiKey),
                usageLogService.summaryByModel());
        return ResponseEntity.ok(body);
    }
}
