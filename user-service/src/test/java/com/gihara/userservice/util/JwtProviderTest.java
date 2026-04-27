package com.gihara.userservice.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtProviderTest {

    private JwtProvider createValidProvider() {
        JwtProvider provider = new JwtProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret",
                "unit_test_secret_that_is_long_enough_for_hs512_and_more_than_64_chars_12345");
        ReflectionTestUtils.setField(provider, "jwtExpirationInMs", 60000L);
        ReflectionTestUtils.invokeMethod(provider, "validateSecret");
        return provider;
    }

    @Test
    void validateSecret_shouldThrow_whenSecretTooShort() {
        JwtProvider provider = new JwtProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", "short");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(provider, "validateSecret"));

        assertEquals("JWT secret must be set and at least 64 characters long", ex.getMessage());
    }

    @Test
    void generateAndValidateToken_shouldWorkForValidToken() {
        JwtProvider jwtProvider = createValidProvider();
        String token = jwtProvider.generateToken("jane@example.com", null, null);

        assertTrue(jwtProvider.validateToken(token));
        assertEquals("jane@example.com", jwtProvider.getEmailFromToken(token));
    }

    @Test
    void validateToken_shouldReturnFalse_forMalformedToken() {
        JwtProvider jwtProvider = createValidProvider();
        assertFalse(jwtProvider.validateToken("not-a-jwt"));
    }

    @Test
    void getExpirationTime_shouldReturnConfiguredValue() {
        JwtProvider jwtProvider = createValidProvider();
        assertEquals(60000L, jwtProvider.getExpirationTime());
    }
}
