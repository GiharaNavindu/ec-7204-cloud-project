package com.gihara.apigateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RequestCorrelationLoggingFilterTest {

    @Test
    void shouldGenerateRequestIdWhenMissing() throws Exception {
        RequestCorrelationLoggingFilter filter = new RequestCorrelationLoggingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/status");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String requestId = response.getHeader("X-Request-Id");
        assertFalse(requestId == null || requestId.isBlank());
    }

    @Test
    void shouldReuseProvidedRequestId() throws Exception {
        RequestCorrelationLoggingFilter filter = new RequestCorrelationLoggingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/status");
        request.addHeader("X-Request-Id", "demo-request-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("demo-request-id", response.getHeader("X-Request-Id"));
    }
}