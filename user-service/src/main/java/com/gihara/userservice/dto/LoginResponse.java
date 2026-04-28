package com.gihara.userservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private String message;
    private String email;
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
}
