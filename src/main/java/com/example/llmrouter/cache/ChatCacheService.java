package com.example.llmrouter.cache;

import com.example.llmrouter.chat.Priority;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Exact-match cache for chat responses, keyed on a normalized prompt plus the routing
 * {@link Priority}. Backed by Redis so cached entries survive application restarts and expire
 * automatically via TTL instead of needing manual eviction.
 * <p>
 * <b>What's in the key:</b> only the two inputs that determine which {@code ModelSpec} ranking
 * the request would receive — the normalized prompt text and the priority. The model/provider
 * that ultimately answered is <em>not</em> part of the key: it's an output of routing, not an
 * input a caller controls, and two requests with identical prompt+priority always produce the
 * same ranking. This also means a response obtained via the fallback chain is cached exactly
 * the same way as a first-try success — {@link CachedChatPayload} stores {@code fallbackUsed}/
 * {@code fallbackReason} so a cache hit still faithfully reports that the original answer came
 * from a fallback, but the cache does not distinguish "fallback" from "first try" when deciding
 * whether to store or serve an entry. The tradeoff: if the top-ranked provider was down when an
 * entry was cached and recovers before the TTL expires, callers keep getting the fallback
 * provider's (cached) answer until the entry expires, rather than being re-routed immediately.
 * <p>
 * <b>Stretch goal (not built):</b> semantic caching, i.e. matching prompts that are not
 * byte-for-byte identical but mean the same thing (e.g. "What's 2+2?" vs "what is 2 + 2"). That
 * would require embedding each prompt into a vector, storing vectors in something that supports
 * approximate nearest-neighbor search (e.g. Redis Stack's vector similarity search, or a
 * dedicated vector DB), and picking a similarity threshold above which a cached answer is
 * considered "close enough" to reuse — a materially different (and riskier, since a false-positive
 * match returns a wrong answer) design than this exact-match cache.
 */
@Service
public class ChatCacheService {

    private static final Logger log = LoggerFactory.getLogger(ChatCacheService.class);
    private static final String KEY_PREFIX = "chat:response:v1:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChatCacheProperties properties;

    public ChatCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
            ChatCacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Optional<CachedChatPayload> get(String prompt, Priority priority) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        String key = buildCacheKey(prompt, priority);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, CachedChatPayload.class));
        } catch (Exception e) {
            // A cache outage/serialization bug should degrade to "miss and call a real provider",
            // never break the request.
            log.warn("Chat cache read failed for key {}; treating as a cache miss", key, e);
            return Optional.empty();
        }
    }

    public void put(String prompt, Priority priority, CachedChatPayload payload) {
        if (!properties.isEnabled()) {
            return;
        }
        String key = buildCacheKey(prompt, priority);
        try {
            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(properties.getTtlSeconds()));
        } catch (JsonProcessingException e) {
            log.warn("Chat cache write failed for key {}; response will not be cached", key, e);
        }
    }

    /**
     * Normalizes a prompt (trim, lowercase, collapse internal whitespace) so that trivially
     * different inputs that mean the same request ("Hi there", "hi   there", "  hi there  ")
     * share one cache entry, then combines it with the priority to build the Redis key. The
     * prompt itself is hashed (not stored raw in the key) to keep key length bounded regardless
     * of the 4000-character prompt limit, and to avoid putting raw user text in Redis key
     * listings/logs.
     */
    static String buildCacheKey(String prompt, Priority priority) {
        String normalized = prompt.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return KEY_PREFIX + priority.name() + ":" + sha256Hex(normalized);
    }

    private static String sha256Hex(String value) {
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
