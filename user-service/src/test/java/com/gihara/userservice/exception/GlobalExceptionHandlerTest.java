package com.gihara.userservice.exception;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleRuntimeException_shouldReturnBadRequest() {
        ResponseEntity<Map<String, Object>> response = handler.handleRuntimeException(new RuntimeException("boom"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().get("status"));
        assertEquals("boom", response.getBody().get("message"));
    }

    @Test
    void handleException_shouldReturnInternalServerError() {
        ResponseEntity<Map<String, Object>> response = handler.handleException(new Exception("hidden"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, response.getBody().get("status"));
        assertEquals("Internal Server Error", response.getBody().get("message"));
    }
}
