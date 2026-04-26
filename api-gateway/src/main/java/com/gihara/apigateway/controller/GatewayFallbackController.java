package com.gihara.apigateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class GatewayFallbackController {

    @GetMapping("/fallback/users")
    public ResponseEntity<Map<String, Object>> usersFallback() {
        return fallback("user-service");
    }

    @GetMapping("/fallback/auctions")
    public ResponseEntity<Map<String, Object>> auctionsFallback() {
        return fallback("auction-service");
    }

    @GetMapping("/fallback/bids")
    public ResponseEntity<Map<String, Object>> bidsFallback() {
        return fallback("bid-service");
    }

    @GetMapping("/fallback/payments")
    public ResponseEntity<Map<String, Object>> paymentsFallback() {
        return fallback("payment-service");
    }

    @GetMapping("/fallback/notifications")
    public ResponseEntity<Map<String, Object>> notificationsFallback() {
        return fallback("notification-service");
    }

    private ResponseEntity<Map<String, Object>> fallback(String service) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                Map.of(
                        "timestamp", Instant.now().toString(),
                        "status", 503,
                        "error", "Service Unavailable",
                        "message", service + " is temporarily unavailable. Please retry later."
                )
        );
    }
}