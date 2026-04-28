package com.gihara.notificationservice.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotificationNotFound_shouldReturnNotFound() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleNotificationNotFound(new NotificationNotFoundException("missing"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, response.getBody().get("status"));
        assertEquals("missing", response.getBody().get("message"));
    }

    @Test
    void handleNotificationAccessDenied_shouldReturnForbidden() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleNotificationAccessDenied(new NotificationAccessDeniedException("denied"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(403, response.getBody().get("status"));
        assertEquals("denied", response.getBody().get("message"));
    }

    @Test
    void handleException_shouldReturnInternalServerError() {
        ResponseEntity<Map<String, Object>> response = handler.handleException(new Exception("hidden"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, response.getBody().get("status"));
        assertEquals("Internal Server Error", response.getBody().get("message"));
    }
}
