package com.example.llmrouter.apikey;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Issues and authenticates API keys. Raw keys are generated from a {@link SecureRandom} source
 * (never derived from anything guessable like a client name or timestamp) and only their SHA-256
 * hash is ever persisted, so {@link #createApiKey} is the one and only moment a raw key is
 * available — callers must save it immediately, same as a cloud provider's "copy this key now,
 * you won't see it again" pattern.
 */
@Service
public class ApiKeyService {

    private static final String KEY_PREFIX = "llmr_";
    private static final int KEY_RANDOM_BYTES = 32;

    private final ApiKeyRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(ApiKeyRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CreatedApiKey createApiKey(String clientName, int rateLimitPerMinute, int dailyQuota) {
        String rawKey = generateRawKey();
        ApiKey saved = repository.save(new ApiKey(clientName, sha256Hex(rawKey), rateLimitPerMinute, dailyQuota));
        return new CreatedApiKey(saved, rawKey);
    }

    /**
     * Looks up the active {@link ApiKey} matching a raw key presented by a client. Returns
     * empty (never throws) for a missing header, an unknown key, or a revoked
     * ({@code active = false}) key — the filter maps all three to the same 401 response so
     * clients can't distinguish "wrong key" from "revoked key" from "no key".
     */
    public Optional<ApiKey> authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        return repository.findByKeyHashAndActiveTrue(sha256Hex(rawKey));
    }

    private String generateRawKey() {
        byte[] randomBytes = new byte[KEY_RANDOM_BYTES];
        secureRandom.nextBytes(randomBytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory JDK algorithm; this is unreachable in practice.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
