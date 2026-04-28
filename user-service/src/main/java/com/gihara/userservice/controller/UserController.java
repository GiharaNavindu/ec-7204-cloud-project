package com.gihara.userservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gihara.userservice.dto.LoginResponse;
import com.gihara.userservice.dto.UserDTO;
import com.gihara.userservice.dto.UserLoginRequest;
import com.gihara.userservice.dto.UserRegistrationRequest;
import com.gihara.userservice.enums.UserRole;
import com.gihara.userservice.service.UserService;

import java.util.List;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/status")
    public String status() {
        return "User Service is up and running!";
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody UserRegistrationRequest request) {
        String response = userService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody UserLoginRequest request) {
        LoginResponse response = userService.login(request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<com.gihara.userservice.dto.TokenRefreshResponse> refreshToken(@RequestBody com.gihara.userservice.dto.TokenRefreshRequest request) {
        return ResponseEntity.ok(userService.refreshToken(request));
    }

    @GetMapping("/protected")
    public ResponseEntity<String> protectedEndpoint(Authentication authentication) {
        return ResponseEntity.ok("Access granted for: " + authentication.getName());
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<String> updateRole(@PathVariable Long id, @RequestParam UserRole newRole) {
        userService.updateRole(id, newRole);
        return ResponseEntity.ok("User role updated successfully to " + newRole);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }
}