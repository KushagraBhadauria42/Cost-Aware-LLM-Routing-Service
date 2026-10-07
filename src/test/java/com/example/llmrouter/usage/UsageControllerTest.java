package com.example.llmrouter.usage;

import com.example.llmrouter.apikey.ApiKey;
import com.example.llmrouter.apikey.ApiKeyAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UsageController.class)
class UsageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsageLogService usageLogService;

    @Test
    void returnsTheCallersOwnClientSummary_andTheGlobalPerModelSummary() throws Exception {
        ApiKey apiKey = mock(ApiKey.class);
        when(apiKey.id()).thenReturn(1L);
        ClientUsageSummary clientSummary = new ClientUsageSummary(1L, "acme", 10L, 3L, 30.0, 0.35, 0.50, 0.15, 30.0);
        when(usageLogService.summaryForClient(apiKey)).thenReturn(clientSummary);
        when(usageLogService.summaryByModel()).thenReturn(
                List.of(new ModelUsageSummary("mock", "mock-model-v1", 10L, 0.0, 120.0)));

        mockMvc.perform(get("/v1/usage")
                        .requestAttr(ApiKeyAuthFilter.AUTHENTICATED_API_KEY_ATTRIBUTE, apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client.clientName").value("acme"))
                .andExpect(jsonPath("$.client.totalRequests").value(10))
                .andExpect(jsonPath("$.client.cacheHitRatePercent").value(30.0))
                .andExpect(jsonPath("$.byModel[0].provider").value("mock"))
                .andExpect(jsonPath("$.byModel[0].totalRequests").value(10));
    }
}
