package com.gihara.apigateway.filter;

import com.gihara.apigateway.config.GatewayRouteProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class JwtValidationFilter extends OncePerRequestFilter {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private final GatewayRouteProperties routeProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtValidationFilter(GatewayRouteProperties routeProperties) {
        this.routeProperties = routeProperties;
    }

    @PostConstruct
    void validateSecret() {
        if (jwtSecret == null || jwtSecret.length() < 64) {
            throw new IllegalStateException("JWT secret must be set and at least 64 characters long");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return routeProperties.isPublicPath(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", "Missing or invalid bearer token", request.getRequestURI());
            return;
        }

        String token = authHeader.substring(7);

        final Claims claims;
        try {
            claims = parseToken(token);
        } catch (JwtException | IllegalArgumentException ex) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", "Invalid or expired token", request.getRequestURI());
            return;
        }

        Set<String> userRoles = extractRoles(claims);
        if (!isAuthorizedForPath(request.getRequestURI(), userRoles)) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden", "Insufficient role for this resource", request.getRequestURI());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Claims parseToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private boolean isAuthorizedForPath(String path, Set<String> userRoles) {
        Set<String> requiredRoles = routeProperties.getRoleRules().stream()
                .filter(rule -> rule.getPattern() != null && pathMatcher.match(rule.getPattern(), path))
                .flatMap(rule -> rule.getRoles().stream())
                .map(this::normalizeRole)
                .collect(Collectors.toSet());

        if (requiredRoles.isEmpty()) {
            return true; // authenticated-only route
        }

        for (String role : userRoles) {
            if (requiredRoles.contains(role)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> extractRoles(Claims claims) {
        Set<String> roles = new HashSet<>();
        addClaimValue(claims.get("roles"), roles);
        addClaimValue(claims.get("authorities"), roles);
        addClaimValue(claims.get("role"), roles);
        addClaimValue(claims.get("scope"), roles);
        return roles.stream().map(this::normalizeRole).collect(Collectors.toSet());
    }

    private void addClaimValue(Object claimValue, Set<String> target) {
        if (claimValue == null) return;

        if (claimValue instanceof String value) {
            for (String part : value.split("[,\\s]+")) {
                if (!part.isBlank()) target.add(part.trim());
            }
            return;
        }

        if (claimValue instanceof Collection<?> values) {
            for (Object item : values) {
                if (item != null) {
                    String s = item.toString().trim();
                    if (!s.isBlank()) target.add(s);
                }
            }
        }
    }

    private String normalizeRole(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase();
        return normalized.startsWith("ROLE_") ? normalized.substring(5) : normalized;
    }

    private void writeError(HttpServletResponse response, int status, String error, String message, String path) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(
                "{"
                        + "\"status\":" + status + ","
                        + "\"error\":\"" + error + "\","
                        + "\"message\":\"" + message + "\","
                        + "\"path\":\"" + path + "\""
                        + "}"
        );
    }
}