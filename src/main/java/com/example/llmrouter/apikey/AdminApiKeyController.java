package com.example.llmrouter.apikey;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Creates API keys. Not protected by {@link ApiKeyAuthFilter} (it only intercepts {@code /v1/*}
 * and this is self-evidently the one endpoint that can't require a key to create a key) —
 * instead guarded by a separate {@code X-Admin-Secret} header checked against
 * {@link AdminProperties}.
 */
@RestController
@RequestMapping("/admin/api-keys")
public class AdminApiKeyController {

    private final ApiKeyService apiKeyService;
    private final AdminProperties adminProperties;

    public AdminApiKeyController(ApiKeyService apiKeyService, AdminProperties adminProperties) {
        this.apiKeyService = apiKeyService;
        this.adminProperties = adminProperties;
    }

    @PostMapping
    public ResponseEntity<CreateApiKeyResponse> create(
            @RequestHeader(value = "X-Admin-Secret", required = false) String providedSecret,
            @Valid @RequestBody CreateApiKeyRequest request) {
        requireValidAdminSecret(providedSecret);

        CreatedApiKey created = apiKeyService.createApiKey(
                request.clientName(), request.rateLimitPerMinute(), request.dailyQuota());

        CreateApiKeyResponse body = new CreateApiKeyResponse(
                created.apiKey().id(),
                created.apiKey().clientName(),
                created.rawKey(),
                created.apiKey().rateLimitPerMinute(),
                created.apiKey().dailyQuota());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    private void requireValidAdminSecret(String providedSecret) {
        String configuredSecret = adminProperties.getSecret();
        if (configuredSecret == null || configuredSecret.isBlank()
                || providedSecret == null
                || !constantTimeEquals(configuredSecret, providedSecret)) {
            throw new InvalidAdminSecretException();
        }
    }

    /** Avoids a timing side-channel that would let an attacker guess the secret byte-by-byte. */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
