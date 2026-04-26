package com.gihara.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.gateway")
public class GatewayRouteProperties {

    private List<String> publicPaths = new ArrayList<>();
    private List<RoleRule> roleRules = new ArrayList<>();
    private RateLimit rateLimit = new RateLimit();

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
        private int maxRequests = 60;
        private long windowSeconds = 60;
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

        public List<String> getExemptPaths() {
            return exemptPaths;
        }

        public void setExemptPaths(List<String> exemptPaths) {
            this.exemptPaths = exemptPaths;
        }
    }
}