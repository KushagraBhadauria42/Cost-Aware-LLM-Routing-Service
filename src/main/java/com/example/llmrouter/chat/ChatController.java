package com.example.llmrouter.chat;

import com.example.llmrouter.apikey.ApiKey;
import com.example.llmrouter.apikey.ApiKeyAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        ApiKey apiKey = (ApiKey) httpRequest.getAttribute(ApiKeyAuthFilter.AUTHENTICATED_API_KEY_ATTRIBUTE);
        return ResponseEntity.ok(chatService.handle(request, apiKey));
    }
}
