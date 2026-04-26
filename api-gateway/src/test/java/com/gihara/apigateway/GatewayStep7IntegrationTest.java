package com.gihara.apigateway;

import com.gihara.apigateway.config.GatewayRouteProperties;
import com.gihara.apigateway.controller.GatewayFallbackController;
import com.gihara.apigateway.filter.GatewayRateLimitFilter;
import com.gihara.apigateway.filter.JwtValidationFilter;
import com.gihara.apigateway.filter.RequestCorrelationLoggingFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GatewayStep7IntegrationTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        GatewayRouteProperties properties = new GatewayRouteProperties();
        properties.setPublicPaths(List.of(
                "/api/users/register",
                "/api/users/login",
                "/api/users/status",
                "/actuator/health/**",
                "/actuator/info",
                "/fallback/**"
        ));

        GatewayRouteProperties.RoleRule adminOnly = new GatewayRouteProperties.RoleRule();
        adminOnly.setPattern("/api/auctions/**");
        adminOnly.setRoles(List.of("ADMIN"));
        properties.setRoleRules(List.of(adminOnly));

        GatewayRouteProperties.RateLimit rateLimit = new GatewayRouteProperties.RateLimit();
        rateLimit.setMode("memory");
        rateLimit.setMaxRequests(2);
        rateLimit.setWindowSeconds(60);
        properties.setRateLimit(rateLimit);

        JwtValidationFilter jwtValidationFilter = new JwtValidationFilter(properties);
        ReflectionTestUtils.setField(jwtValidationFilter, "jwtSecret", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

        mockMvc = MockMvcBuilders
                .standaloneSetup(new GatewayTestController(), new GatewayFallbackController())
                .addFilters(new RequestCorrelationLoggingFilter(), new GatewayRateLimitFilter(properties), jwtValidationFilter)
                .build();
    }

    @Test
    void shouldExposeCorrelationIdOnPublicRoute() throws Exception {
        mockMvc.perform(get("/api/users/status"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void shouldRejectProtectedRouteWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/api/auctions/test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void shouldReturn429WithRetryHeadersWhenRateLimitIsExceeded() throws Exception {
        mockMvc.perform(get("/api/auctions/test"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auctions/test"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auctions/test"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-Rate-Limit-Limit", "2"))
                .andExpect(header().string("X-Rate-Limit-Remaining", "0"))
            .andExpect(header().exists("Retry-After"));
    }

    @Test
    void shouldReturnFallbackPayloadWhenDownstreamServiceIsUnavailable() throws Exception {
        mockMvc.perform(get("/fallback/users"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"));
    }

    @RestController
    static class GatewayTestController {
        @GetMapping("/api/users/status")
        Map<String, String> status() {
            return Map.of("status", "ok");
        }
    }
}