package com.gihara.apigateway.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GatewayFallbackControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new GatewayFallbackController()).build();

    @Test
    void usersFallbackShouldReturn503Payload() throws Exception {
        mockMvc.perform(get("/fallback/users"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").value("user-service is temporarily unavailable. Please retry later."));
    }

    @Test
    void notificationsFallbackShouldReturn503Payload() throws Exception {
        mockMvc.perform(get("/fallback/notifications"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"));
    }
}