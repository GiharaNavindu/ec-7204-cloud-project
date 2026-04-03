package com.gihara.userservice.dto;

public record UserRegistrationRequest(
        String name,
        String email,
        String password
) {}
