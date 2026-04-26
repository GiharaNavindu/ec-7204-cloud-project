package com.gihara.apigateway.filter;

import com.gihara.apigateway.config.GatewayRouteProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayRateLimitFilterTest {

    @Test
    void shouldAllowRequestsUntilLimitIsReached() throws Exception {
        GatewayRouteProperties properties = new GatewayRouteProperties();
        GatewayRouteProperties.RateLimit rateLimit = new GatewayRouteProperties.RateLimit();
        rateLimit.setMaxRequests(2);
        rateLimit.setWindowSeconds(60);
        properties.setRateLimit(rateLimit);

        GatewayRateLimitFilter filter = new GatewayRateLimitFilter(properties);
        AtomicInteger downstreamCalls = new AtomicInteger();

        assertAllowed(filter, downstreamCalls, "/api/auctions/test");
        assertAllowed(filter, downstreamCalls, "/api/auctions/test");

        MockHttpServletRequest thirdRequest = request("/api/auctions/test");
        MockHttpServletResponse thirdResponse = new MockHttpServletResponse();
        filter.doFilter(thirdRequest, thirdResponse, passthroughChain(downstreamCalls));

        assertEquals(429, thirdResponse.getStatus());
        assertTrue(thirdResponse.getContentAsString().contains("Rate limit exceeded"));
        assertEquals(2, downstreamCalls.get());
    }

    @Test
    void shouldSkipRateLimitingForExemptPaths() throws Exception {
        GatewayRouteProperties properties = new GatewayRouteProperties();
        GatewayRouteProperties.RateLimit rateLimit = new GatewayRouteProperties.RateLimit();
        rateLimit.setMaxRequests(1);
        rateLimit.setWindowSeconds(60);
        properties.setRateLimit(rateLimit);

        GatewayRateLimitFilter filter = new GatewayRateLimitFilter(properties);
        AtomicInteger downstreamCalls = new AtomicInteger();

        MockHttpServletRequest request = request("/actuator/health/liveness");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, passthroughChain(downstreamCalls));

        assertEquals(200, response.getStatus());
        assertEquals(1, downstreamCalls.get());
    }

    @Test
    void shouldUseRedisCounterWhenRedisModeIsEnabled() throws Exception {
        GatewayRouteProperties properties = new GatewayRouteProperties();
        GatewayRouteProperties.RateLimit rateLimit = new GatewayRouteProperties.RateLimit();
        rateLimit.setMode("redis");
        rateLimit.setMaxRequests(2);
        rateLimit.setWindowSeconds(60);
        properties.setRateLimit(rateLimit);

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L, 2L, 3L);
        when(redisTemplate.expire(anyString(), eq(60L), eq(TimeUnit.MILLISECONDS))).thenReturn(Boolean.TRUE);
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(60L);

        GatewayRateLimitFilter filter = new GatewayRateLimitFilter(properties, redisTemplate);
        AtomicInteger downstreamCalls = new AtomicInteger();

        assertAllowed(filter, downstreamCalls, "/api/auctions/test");
        assertAllowed(filter, downstreamCalls, "/api/auctions/test");

        MockHttpServletRequest thirdRequest = request("/api/auctions/test");
        MockHttpServletResponse thirdResponse = new MockHttpServletResponse();
        filter.doFilter(thirdRequest, thirdResponse, passthroughChain(downstreamCalls));

        assertEquals(429, thirdResponse.getStatus());
        assertEquals("2", thirdResponse.getHeader("X-Rate-Limit-Limit"));
        assertEquals("0", thirdResponse.getHeader("X-Rate-Limit-Remaining"));
        assertEquals("60", thirdResponse.getHeader("X-Rate-Limit-Reset"));
        assertEquals("60", thirdResponse.getHeader("Retry-After"));
        assertEquals(2, downstreamCalls.get());
    }

    private void assertAllowed(GatewayRateLimitFilter filter, AtomicInteger downstreamCalls, String path) throws Exception {
        MockHttpServletRequest request = request(path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, passthroughChain(downstreamCalls));
        assertEquals(200, response.getStatus());
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr("10.10.10.10");
        return request;
    }

    private FilterChain passthroughChain(AtomicInteger downstreamCalls) {
        return (request, response) -> downstreamCalls.incrementAndGet();
    }
}