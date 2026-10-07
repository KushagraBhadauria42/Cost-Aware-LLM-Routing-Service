package com.example.llmrouter.apikey;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminApiKeyController.class)
@EnableConfigurationProperties(AdminProperties.class)
@TestPropertySource(properties = "llm.admin.secret=test-admin-secret")
class AdminApiKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ApiKeyService apiKeyService;

    @Test
    void returns401_whenAdminSecretHeaderIsMissing() throws Exception {
        mockMvc.perform(post("/admin/api-keys")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateApiKeyRequest("acme", null, null))))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(apiKeyService);
    }

    @Test
    void returns401_whenAdminSecretHeaderIsWrong() throws Exception {
        mockMvc.perform(post("/admin/api-keys")
                        .header("X-Admin-Secret", "wrong-secret")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateApiKeyRequest("acme", null, null))))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(apiKeyService);
    }

    @Test
    void returns400_whenClientNameIsBlank() throws Exception {
        mockMvc.perform(post("/admin/api-keys")
                        .header("X-Admin-Secret", "test-admin-secret")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateApiKeyRequest("", null, null))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(apiKeyService);
    }

    @Test
    void returns201WithTheRawApiKey_whenAdminSecretIsCorrect() throws Exception {
        ApiKey apiKey = new ApiKey("acme", "deadbeef", 60, 1000);
        CreatedApiKey created = new CreatedApiKey(apiKey, "llmr_raw-key-value");
        when(apiKeyService.createApiKey("acme", 60, 1000)).thenReturn(created);

        mockMvc.perform(post("/admin/api-keys")
                        .header("X-Admin-Secret", "test-admin-secret")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateApiKeyRequest("acme", 60, 1000))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apiKey").value("llmr_raw-key-value"))
                .andExpect(jsonPath("$.clientName").value("acme"))
                .andExpect(jsonPath("$.rateLimitPerMinute").value(60))
                .andExpect(jsonPath("$.dailyQuota").value(1000));

        verify(apiKeyService).createApiKey(eq("acme"), eq(60), eq(1000));
    }

    @Test
    void appliesDefaultRateLimitAndQuota_whenNotProvidedInRequest() throws Exception {
        ApiKey apiKey = new ApiKey("acme", "deadbeef", 60, 1000);
        when(apiKeyService.createApiKey("acme", 60, 1000)).thenReturn(new CreatedApiKey(apiKey, "llmr_raw"));

        mockMvc.perform(post("/admin/api-keys")
                        .header("X-Admin-Secret", "test-admin-secret")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateApiKeyRequest("acme", null, null))))
                .andExpect(status().isCreated());

        verify(apiKeyService).createApiKey("acme", 60, 1000);
    }
}
