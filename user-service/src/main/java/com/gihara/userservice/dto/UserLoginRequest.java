package com.gihara.userservice.dto;

public record UserLoginRequest(
        String email,
        String password
) {}
