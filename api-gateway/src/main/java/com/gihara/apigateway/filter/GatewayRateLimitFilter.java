package com.gihara.apigateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gihara.apigateway.config.GatewayRouteProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class GatewayRateLimitFilter extends OncePerRequestFilter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final GatewayRouteProperties routeProperties;
    private final ConcurrentMap<String, ClientWindow> clientWindows = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;

    public GatewayRateLimitFilter(GatewayRouteProperties routeProperties) {
        this(routeProperties, null);
    }

    @Autowired
    public GatewayRateLimitFilter(GatewayRouteProperties routeProperties, StringRedisTemplate redisTemplate) {
        this.routeProperties = routeProperties;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }

        GatewayRouteProperties.RateLimit rateLimit = routeProperties.getRateLimit();
        if (rateLimit == null || !rateLimit.isEnabled()) {
            return true;
        }

        String path = request.getRequestURI();
        return routeProperties.isRateLimitExemptPath(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        GatewayRouteProperties.RateLimit rateLimit = routeProperties.getRateLimit();
        long now = Instant.now().toEpochMilli();
        String clientKey = resolveClientKey(request);
        long windowMillis = rateLimit.getWindowSeconds() * 1000L;

        RateLimitSnapshot snapshot = acquireToken(rateLimit, clientKey, now, windowMillis);

        if (!snapshot.allowed) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.setHeader("X-Rate-Limit-Limit", String.valueOf(rateLimit.getMaxRequests()));
            response.setHeader("X-Rate-Limit-Remaining", "0");
            response.setHeader("X-Rate-Limit-Reset", String.valueOf(snapshot.resetSeconds));
            response.setHeader("Retry-After", String.valueOf(snapshot.retryAfterSeconds));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", 429);
            body.put("error", "Too Many Requests");
            body.put("message", "Rate limit exceeded. Please retry later.");
            body.put("path", request.getRequestURI());
            body.put("clientKey", clientKey);
            body.put("limit", rateLimit.getMaxRequests());
            body.put("windowSeconds", rateLimit.getWindowSeconds());
            response.getWriter().write(OBJECT_MAPPER.writeValueAsString(body));
            return;
        }

        response.setHeader("X-Rate-Limit-Limit", String.valueOf(rateLimit.getMaxRequests()));
        response.setHeader("X-Rate-Limit-Remaining", String.valueOf(snapshot.remaining));
        response.setHeader("X-Rate-Limit-Reset", String.valueOf(snapshot.resetSeconds));

        try {
            filterChain.doFilter(request, response);
        } finally {
            cleanupIfIdle(clientKey, now, windowMillis);
        }
    }

    private RateLimitSnapshot acquireToken(GatewayRouteProperties.RateLimit rateLimit,
                                           String clientKey,
                                           long now,
                                           long windowMillis) {
        String mode = rateLimit.getMode() == null ? "memory" : rateLimit.getMode().trim().toLowerCase();

        if ("redis".equals(mode)) {
            try {
                return acquireFromRedis(rateLimit, clientKey, windowMillis);
            } catch (RedisConnectionFailureException | IllegalStateException ex) {
                if (!rateLimit.isFailOpen()) {
                    return RateLimitSnapshot.blocked(rateLimit.getMaxRequests(), windowMillis, windowMillis);
                }
                return acquireFromMemory(rateLimit, clientKey, now, windowMillis);
            }
        }

        return acquireFromMemory(rateLimit, clientKey, now, windowMillis);
    }

    private RateLimitSnapshot acquireFromRedis(GatewayRouteProperties.RateLimit rateLimit,
                                               String clientKey,
                                               long windowMillis) {
        if (redisTemplate == null) {
            throw new IllegalStateException("Redis mode enabled but StringRedisTemplate is not configured");
        }

        String key = rateLimit.getRedisKeyPrefix() + clientKey;
        Long current = redisTemplate.opsForValue().increment(key);
        if (current == null) {
            throw new IllegalStateException("Unable to increment Redis rate limit key");
        }

        if (current == 1L) {
            redisTemplate.expire(key, windowMillis, TimeUnit.MILLISECONDS);
        }

        Long ttlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        long resetSeconds = ttlSeconds == null || ttlSeconds < 1 ? 1 : ttlSeconds;

        if (current > rateLimit.getMaxRequests()) {
            return RateLimitSnapshot.blocked((int) current.longValue(), resetSeconds * 1000L, windowMillis);
        }

        int remaining = Math.max(0, rateLimit.getMaxRequests() - current.intValue());
        return RateLimitSnapshot.allowed(remaining, resetSeconds * 1000L);
    }

    private RateLimitSnapshot acquireFromMemory(GatewayRouteProperties.RateLimit rateLimit,
                                                String clientKey,
                                                long now,
                                                long windowMillis) {
        ClientWindow window = clientWindows.computeIfAbsent(clientKey, key -> new ClientWindow());
        synchronized (window) {
            return window.tryAcquire(now, windowMillis, rateLimit.getMaxRequests());
        }
    }

    private String resolveClientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }

    private void cleanupIfIdle(String clientKey, long now, long windowMillis) {
        ClientWindow window = clientWindows.get(clientKey);
        if (window != null) {
            synchronized (window) {
                window.evictExpired(now, windowMillis);
                if (window.isEmpty()) {
                    clientWindows.remove(clientKey, window);
                }
            }
        }
    }

    private static class ClientWindow {

        private final Deque<Long> requests = new ArrayDeque<>();

        RateLimitSnapshot tryAcquire(long now, long windowMillis, int maxRequests) {
            evictExpired(now, windowMillis);

            if (requests.size() >= maxRequests) {
                long oldest = requests.peekFirst();
                long retryAfterMillis = Math.max(1L, windowMillis - (now - oldest));
                return RateLimitSnapshot.blocked(requests.size(), retryAfterMillis, windowMillis);
            }

            requests.addLast(now);
            long oldest = requests.peekFirst();
            long resetMillis = Math.max(1L, windowMillis - (now - oldest));
            int remaining = Math.max(0, maxRequests - requests.size());
            return RateLimitSnapshot.allowed(remaining, resetMillis);
        }

        void evictExpired(long now, long windowMillis) {
            while (!requests.isEmpty() && now - requests.peekFirst() >= windowMillis) {
                requests.removeFirst();
            }
        }

        boolean isEmpty() {
            return requests.isEmpty();
        }
    }

    private static class RateLimitSnapshot {
        private final boolean allowed;
        private final int remaining;
        private final long resetSeconds;
        private final long retryAfterSeconds;

        private RateLimitSnapshot(boolean allowed, int remaining, long resetSeconds, long retryAfterSeconds) {
            this.allowed = allowed;
            this.remaining = remaining;
            this.resetSeconds = resetSeconds;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        static RateLimitSnapshot allowed(int remaining, long resetMillis) {
            return new RateLimitSnapshot(true, remaining, Math.max(1L, resetMillis / 1000L), 0L);
        }

        static RateLimitSnapshot blocked(int currentCount, long retryAfterMillis, long windowMillis) {
            long retryAfterSeconds = Math.max(1L, retryAfterMillis / 1000L);
            return new RateLimitSnapshot(false, 0, Math.max(1L, windowMillis / 1000L), retryAfterSeconds);
        }
    }
}