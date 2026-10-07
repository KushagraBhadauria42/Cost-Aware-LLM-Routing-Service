package com.example.llmrouter.apikey;

import com.example.llmrouter.error.ApiError;
import com.example.llmrouter.ratelimit.RateLimitResult;
import com.example.llmrouter.ratelimit.RateLimiterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Authenticates every request under {@code /v1/*} via the {@code X-API-Key} header, then applies
 * the matched key's rate limit / daily quota. Registered manually (not {@code @Component}-scanned)
 * via {@link ApiKeyFilterConfig} so its URL pattern can be restricted to {@code /v1/*} — it must
 * not intercept {@code /admin/*}, {@code /api/ping}, or {@code /actuator/*}.
 * <p>
 * Runs before the {@code DispatcherServlet}, so failures here are written directly to the
 * response (matching {@link ApiError}'s shape) rather than thrown for
 * {@code GlobalExceptionHandler} to catch — a {@code @RestControllerAdvice} only sees exceptions
 * raised during request *handling*, not during servlet filter processing that happens earlier.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";

    /** Request attribute the authenticated {@link ApiKey} is published under, for downstream
     * controllers (e.g. {@code ChatController}, {@code UsageController}) that need to know which
     * client made the request without re-parsing the header or re-querying Postgres. */
    public static final String AUTHENTICATED_API_KEY_ATTRIBUTE = ApiKeyAuthFilter.class.getName() + ".API_KEY";

    private final ApiKeyService apiKeyService;
    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(ApiKeyService apiKeyService, RateLimiterService rateLimiterService,
            ObjectMapper objectMapper) {
        this.apiKeyService = apiKeyService;
        this.rateLimiterService = rateLimiterService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<ApiKey> apiKey = apiKeyService.authenticate(request.getHeader(API_KEY_HEADER));
        if (apiKey.isEmpty()) {
            writeError(response, HttpStatus.UNAUTHORIZED,
                    "Missing or invalid " + API_KEY_HEADER + " header", request.getRequestURI(), null);
            return;
        }

        ApiKey key = apiKey.get();
        RateLimitResult result = rateLimiterService.checkAndIncrement(
                key.id().toString(), key.rateLimitPerMinute(), key.dailyQuota());
        if (!result.allowed()) {
            String message = RateLimitResult.DAILY_QUOTA.equals(result.limitType())
                    ? "Daily quota exceeded; retry after " + result.retryAfterSeconds() + "s"
                    : "Rate limit exceeded; retry after " + result.retryAfterSeconds() + "s";
            writeError(response, HttpStatus.TOO_MANY_REQUESTS, message, request.getRequestURI(),
                    result.retryAfterSeconds());
            return;
        }

        request.setAttribute(AUTHENTICATED_API_KEY_ATTRIBUTE, key);
        chain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message, String path,
            Long retryAfterSeconds) throws IOException {
        if (retryAfterSeconds != null) {
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiError body = ApiError.of(status.value(), status.getReasonPhrase(), message, path);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
