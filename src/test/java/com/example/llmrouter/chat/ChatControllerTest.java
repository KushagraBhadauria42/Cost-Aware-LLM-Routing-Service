package com.example.llmrouter.chat;

import com.example.llmrouter.provider.ProviderException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ChatService chatService;

    @Test
    void returns200WithResponseBody_whenRequestIsValid() throws Exception {
        given(chatService.handle(any(ChatRequest.class), any()))
                .willReturn(new ChatResponse("hi", "mock", "mock-model-v1", 2, 3, 5, 10, Priority.AUTO, "test reason", false, null, false));

        mockMvc.perform(post("/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("hello", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseText").value("hi"))
                .andExpect(jsonPath("$.provider").value("mock"))
                .andExpect(jsonPath("$.model").value("mock-model-v1"))
                .andExpect(jsonPath("$.totalTokens").value(5))
                .andExpect(jsonPath("$.priorityUsed").value("AUTO"));
    }

    @Test
    void returns400WithFieldErrors_whenPromptIsBlank() throws Exception {
        mockMvc.perform(post("/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt": "", "priority": "fast"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.prompt").exists());
    }

    @Test
    void acceptsLowercasePriority_caseInsensitively() throws Exception {
        given(chatService.handle(any(ChatRequest.class), any()))
                .willReturn(new ChatResponse("hi", "mock", "mock-model-v1", 1, 1, 2, 5, Priority.FAST, "test reason", false, null, false));

        mockMvc.perform(post("/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt": "hello", "priority": "fast"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priorityUsed").value("FAST"));
    }

    @Test
    void returns502_whenProviderFails() throws Exception {
        given(chatService.handle(any(ChatRequest.class), any()))
                .willThrow(new ProviderException("mock", "simulated failure"));

        mockMvc.perform(post("/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("hello", null))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").value("Provider 'mock' failed: simulated failure"));
    }
}
