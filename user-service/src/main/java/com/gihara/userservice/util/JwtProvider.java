package com.gihara.userservice.util;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.gihara.userservice.enums.UserRole;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

@Component
public class JwtProvider {

    // JWT secret
    @Value("${app.jwt.secret}")
    private String jwtSecret;

    // Access token expiration (15 minutes)
    private final long accessTokenExpirationInMs = 15 * 60 * 1000;

    // Refresh token expiration (7 days)
    private final long refreshTokenExpirationInMs = 7L * 24 * 60 * 60 * 1000;

    @PostConstruct
    void validateSecret() {
        // Validation check - JWT secret must be 64 characters
        if (jwtSecret == null || jwtSecret.length() < 64) {
            throw new IllegalStateException("JWT secret must be set and at least 64 characters long");
        }
    }

    public String generateToken(String email, Long userId, UserRole role) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        return Jwts.builder()
                .setSubject(email)
                .claim("userId", userId)
                .claim("role", role.name())
                .claim("roles", java.util.Collections.singletonList(role.name())) // Added for gateway compatibility
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessTokenExpirationInMs))
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
    }

    public String getEmailFromToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        
        return claims.getSubject();
    }

    public String getRoleFromToken(String token) {
    SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
    Claims claims = Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .getBody();
    return claims.get("role", String.class);
}

    public boolean validateToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public long getAccessTokenExpirationTime() {
        return accessTokenExpirationInMs;
    }

    public long getRefreshTokenExpirationTime() {
        return refreshTokenExpirationInMs;
    }
}
