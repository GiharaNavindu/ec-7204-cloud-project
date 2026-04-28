package com.gihara.userservice.dto;

import com.gihara.userservice.enums.UserRole;

public record UserDTO(
        Long id,
        String name,
        String email,
        UserRole role
) {}
