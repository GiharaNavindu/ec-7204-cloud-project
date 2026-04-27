package com.gihara.userservice.security;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.gihara.userservice.util.JwtProvider;

import jakarta.servlet.ServletException;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtProvider jwtProvider;

    @Test
    void doFilter_shouldSetAuthentication_whenTokenIsValid() throws ServletException, IOException {
        SecurityContextHolder.clearContext();
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(jwtProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtProvider.getEmailFromToken("valid-token")).thenReturn("jane@example.com");

        filter.doFilter(request, response, chain);

        assertEquals("jane@example.com", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(jwtProvider).validateToken("valid-token");
        verify(jwtProvider).getEmailFromToken("valid-token");
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_shouldNotSetAuthentication_whenAuthorizationHeaderMissing() throws ServletException, IOException {
        SecurityContextHolder.clearContext();
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_shouldNotSetAuthentication_whenTokenIsInvalid() throws ServletException, IOException {
        SecurityContextHolder.clearContext();
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(jwtProvider.validateToken("bad-token")).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtProvider).validateToken("bad-token");
        SecurityContextHolder.clearContext();
    }
}
