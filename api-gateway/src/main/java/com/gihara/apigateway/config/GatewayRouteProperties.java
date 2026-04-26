package com.gihara.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.gateway")
public class GatewayRouteProperties {

    private List<String> publicPaths = new ArrayList<>();
    private List<RoleRule> roleRules = new ArrayList<>();

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
}