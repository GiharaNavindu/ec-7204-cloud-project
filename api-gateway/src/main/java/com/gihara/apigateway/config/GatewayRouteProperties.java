package com.gihara.apigateway.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "app.gateway")
public class GatewayRouteProperties {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private List<String> publicPaths = new ArrayList<>();
    private List<RoleRule> roleRules = new ArrayList<>();
    private RateLimit rateLimit = new RateLimit();
    private Cors cors = new Cors();

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    public List<RoleRule> getRoleRules() {
        return roleRules;
    }

    public void setRoleRules(List<RoleRule> roleRules) {
        this.roleRules = roleRules;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    public boolean isPublicPath(String requestUri) {
        return matchesAnyPattern(publicPaths, requestUri);
    }

    public boolean isRateLimitExemptPath(String requestUri) {
        return matchesAnyPattern(rateLimit.getExemptPaths(), requestUri);
    }

    private boolean matchesAnyPattern(List<String> patterns, String requestUri) {
        if (!StringUtils.hasText(requestUri) || patterns == null || patterns.isEmpty()) {
            return false;
        }

        String path = requestUri.trim();
        return patterns.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    public static class RoleRule {
        private String pattern;
        private List<String> roles = new ArrayList<>();

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }
    }

    public static class RateLimit {
        private boolean enabled = true;
        private String mode = "redis";
        private int maxRequests = 60;
        private long windowSeconds = 60;
        private String redisKeyPrefix = "gateway:ratelimit:";
        private boolean failOpen = true;
        private List<String> exemptPaths = new ArrayList<>(List.of(
            "/actuator/health/**",
            "/actuator/info",
            "/fallback/**"
        ));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public int getMaxRequests() {
            return maxRequests;
        }

        public void setMaxRequests(int maxRequests) {
            this.maxRequests = maxRequests;
        }

        public long getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(long windowSeconds) {
            this.windowSeconds = windowSeconds;
        }

        public String getRedisKeyPrefix() {
            return redisKeyPrefix;
        }

        public void setRedisKeyPrefix(String redisKeyPrefix) {
            this.redisKeyPrefix = redisKeyPrefix;
        }

        public boolean isFailOpen() {
            return failOpen;
        }

        public void setFailOpen(boolean failOpen) {
            this.failOpen = failOpen;
        }

        public List<String> getExemptPaths() {
            return exemptPaths;
        }

        public void setExemptPaths(List<String> exemptPaths) {
            this.exemptPaths = exemptPaths;
        }
    }

    public static class Cors {
        private List<String> allowedOriginPatterns = new ArrayList<>();
        private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        private List<String> allowedHeaders = new ArrayList<>(List.of("Authorization", "Content-Type", "X-Request-Id", "X-Forwarded-For"));
        private List<String> exposedHeaders = new ArrayList<>(List.of("X-Request-Id", "X-Rate-Limit-Limit", "X-Rate-Limit-Remaining", "X-Rate-Limit-Reset", "Retry-After"));
        private boolean allowCredentials = true;
        private long maxAgeSeconds = 1800;

        public List<String> getAllowedOriginPatterns() {
            return allowedOriginPatterns;
        }

        public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
            this.allowedOriginPatterns = allowedOriginPatterns;
        }

        public List<String> getAllowedMethods() {
            return allowedMethods;
        }

        public void setAllowedMethods(List<String> allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        public List<String> getAllowedHeaders() {
            return allowedHeaders;
        }

        public void setAllowedHeaders(List<String> allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        public List<String> getExposedHeaders() {
            return exposedHeaders;
        }

        public void setExposedHeaders(List<String> exposedHeaders) {
            this.exposedHeaders = exposedHeaders;
        }

        public boolean isAllowCredentials() {
            return allowCredentials;
        }

        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }

        public long getMaxAgeSeconds() {
            return maxAgeSeconds;
        }

        public void setMaxAgeSeconds(long maxAgeSeconds) {
            this.maxAgeSeconds = maxAgeSeconds;
        }
    }
}