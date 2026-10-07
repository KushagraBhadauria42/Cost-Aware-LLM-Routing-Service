package com.example.llmrouter.apikey;

import com.example.llmrouter.ratelimit.RateLimitResult;
import com.example.llmrouter.ratelimit.RateLimiterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthFilterTest {

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private RateLimiterService rateLimiterService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private ApiKeyAuthFilter filter() {
        return new ApiKeyAuthFilter(apiKeyService, rateLimiterService, objectMapper);
    }

    @Test
    void returns401_andNeverCallsTheChain_whenApiKeyHeaderIsMissing() throws Exception {
        when(apiKeyService.authenticate(null)).thenReturn(Optional.empty());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/chat");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Missing or invalid X-API-Key header");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void returns401_whenApiKeyHeaderDoesNotMatchAnyActiveKey() throws Exception {
        when(apiKeyService.authenticate("llmr_bogus")).thenReturn(Optional.empty());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/chat");
        request.addHeader("X-API-Key", "llmr_bogus");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void returns429WithRetryAfterHeader_whenRateLimited() throws Exception {
        ApiKey apiKey = mock(ApiKey.class);
        when(apiKey.id()).thenReturn(42L);
        when(apiKey.rateLimitPerMinute()).thenReturn(5);
        when(apiKey.dailyQuota()).thenReturn(1000);
        when(apiKeyService.authenticate("llmr_valid")).thenReturn(Optional.of(apiKey));
        when(rateLimiterService.checkAndIncrement("42", 5, 1000))
                .thenReturn(RateLimitResult.blocked(RateLimitResult.RATE_LIMIT_PER_MINUTE, 37));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/chat");
        request.addHeader("X-API-Key", "llmr_valid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("37");
        assertThat(response.getContentAsString()).contains("Rate limit exceeded");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void returns429WithRetryAfterHeader_whenDailyQuotaExceeded() throws Exception {
        ApiKey apiKey = mock(ApiKey.class);
        when(apiKey.id()).thenReturn(7L);
        when(apiKey.rateLimitPerMinute()).thenReturn(60);
        when(apiKey.dailyQuota()).thenReturn(100);
        when(apiKeyService.authenticate("llmr_valid")).thenReturn(Optional.of(apiKey));
        when(rateLimiterService.checkAndIncrement("7", 60, 100))
                .thenReturn(RateLimitResult.blocked(RateLimitResult.DAILY_QUOTA, 3600));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/chat");
        request.addHeader("X-API-Key", "llmr_valid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("3600");
        assertThat(response.getContentAsString()).contains("Daily quota exceeded");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void callsTheChain_whenApiKeyIsValidAndWithinLimits() throws Exception {
        ApiKey apiKey = mock(ApiKey.class);
        when(apiKey.id()).thenReturn(1L);
        when(apiKey.rateLimitPerMinute()).thenReturn(60);
        when(apiKey.dailyQuota()).thenReturn(1000);
        when(apiKeyService.authenticate("llmr_valid")).thenReturn(Optional.of(apiKey));
        when(rateLimiterService.checkAndIncrement(anyString(), anyInt(), anyInt()))
                .thenReturn(RateLimitResult.allow());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/chat");
        request.addHeader("X-API-Key", "llmr_valid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
