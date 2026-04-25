package com.gihara.userservice.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UserControllerIT {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String baseUrl(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpResponse<String> postJson(String path, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl(path)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl(path)))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getWithAuthHeader(String path, String authHeader) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl(path)))
                .header("Authorization", authHeader)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void status_shouldBePublicAndReturnHealthyMessage() throws Exception {
        HttpResponse<String> response = get("/api/users/status");

        assertEquals(200, response.statusCode());
        assertEquals("User Service is up and running!", response.body());
    }

    @Test
    void registerAndLogin_shouldSucceed_andReturnJwtToken() throws Exception {
        String registerJson = """
                {
                  "name": "Alice",
                  "email": "alice@example.com",
                  "password": "StrongPass123"
                }
                """;

        HttpResponse<String> registerResponse = postJson("/api/users/register", registerJson);
        assertEquals(201, registerResponse.statusCode());

        String loginJson = """
                {
                  "email": "alice@example.com",
                  "password": "StrongPass123"
                }
                """;

        HttpResponse<String> loginResponse = postJson("/api/users/login", loginJson);
        Map<String, Object> body = objectMapper.readValue(loginResponse.body(), new TypeReference<>() {
        });

        assertEquals(200, loginResponse.statusCode());
        assertEquals("Login successful!", body.get("message"));
        assertEquals("alice@example.com", body.get("email"));
        assertNotNull(body.get("token"));
        assertNotNull(body.get("expiresIn"));
    }

    @Test
    void register_shouldReturnBadRequest_whenEmailIsDuplicate() throws Exception {
        String registerJson = """
                {
                  "name": "Bob",
                  "email": "bob@example.com",
                  "password": "StrongPass123"
                }
                """;

        HttpResponse<String> first = postJson("/api/users/register", registerJson);
        assertEquals(201, first.statusCode());

        HttpResponse<String> second = postJson("/api/users/register", registerJson);
        Map<String, Object> body = objectMapper.readValue(second.body(), new TypeReference<>() {
        });

        assertEquals(400, second.statusCode());
        assertTrue(String.valueOf(body.get("message")).contains("Email is already in use"));
    }

    @Test
    void login_shouldReturnBadRequest_whenUserDoesNotExist() throws Exception {
        String loginJson = """
                {
                  "email": "nobody@example.com",
                  "password": "StrongPass123"
                }
                """;

        HttpResponse<String> response = postJson("/api/users/login", loginJson);
        Map<String, Object> body = objectMapper.readValue(response.body(), new TypeReference<>() {
        });

        assertEquals(400, response.statusCode());
        assertTrue(String.valueOf(body.get("message")).contains("User not found with email"));
    }

    @Test
    void login_shouldReturnBadRequest_whenPasswordIsInvalid() throws Exception {
        String registerJson = """
                {
                  "name": "Charlie",
                  "email": "charlie@example.com",
                  "password": "Correct123"
                }
                """;

        postJson("/api/users/register", registerJson);

        String loginJson = """
                {
                  "email": "charlie@example.com",
                  "password": "Wrong123"
                }
                """;

        HttpResponse<String> response = postJson("/api/users/login", loginJson);
        Map<String, Object> body = objectMapper.readValue(response.body(), new TypeReference<>() {
        });

        assertEquals(400, response.statusCode());
        assertEquals("Invalid password!", body.get("message"));
    }

    @Test
    void protected_shouldRejectRequest_withoutJwt() throws Exception {
        HttpResponse<String> response = get("/api/users/protected");

        assertEquals(403, response.statusCode());
    }

    @Test
    void protected_shouldRejectRequest_withMalformedAuthorizationHeader() throws Exception {
        HttpResponse<String> response = getWithAuthHeader("/api/users/protected", "Token abc.def.ghi");

        assertEquals(403, response.statusCode());
    }

    @Test
    void protected_shouldRejectRequest_withInvalidJwt() throws Exception {
        HttpResponse<String> response = getWithAuthHeader("/api/users/protected", "Bearer invalid.jwt.token");

        assertEquals(403, response.statusCode());
    }

    @Test
    void protected_shouldAllowRequest_withValidJwt() throws Exception {
        String registerJson = """
                {
                  "name": "Diana",
                  "email": "diana@example.com",
                  "password": "StrongPass123"
                }
                """;

        postJson("/api/users/register", registerJson);

        String loginJson = """
                {
                  "email": "diana@example.com",
                  "password": "StrongPass123"
                }
                """;

        HttpResponse<String> loginResponse = postJson("/api/users/login", loginJson);
        Map<String, Object> loginBody = objectMapper.readValue(loginResponse.body(), new TypeReference<>() {
        });
        String token = String.valueOf(loginBody.get("token"));

        HttpResponse<String> protectedResponse = getWithAuthHeader("/api/users/protected", "Bearer " + token);

        assertEquals(200, protectedResponse.statusCode());
        assertTrue(protectedResponse.body().contains("Access granted for: diana@example.com"));
    }
}
