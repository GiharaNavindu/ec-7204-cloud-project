package com.gihara.userservice.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gihara.userservice.dto.LoginResponse;
import com.gihara.userservice.dto.UserDTO;
import com.gihara.userservice.dto.UserLoginRequest;
import com.gihara.userservice.dto.UserRegistrationRequest;
import com.gihara.userservice.dto.TokenRefreshRequest;
import com.gihara.userservice.dto.TokenRefreshResponse;
import com.gihara.userservice.entity.User;
import com.gihara.userservice.entity.RefreshToken;
import com.gihara.userservice.enums.UserRole;
import com.gihara.userservice.enums.UserStatus;
import com.gihara.userservice.repository.UserRepository;
import com.gihara.userservice.util.JwtProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    public String registerUser(UserRegistrationRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new RuntimeException("Email is already in use!");
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        
        User newUser = User.builder()
                .username(request.name())
                .email(request.email())
                .password(encodedPassword)
                .userRole(UserRole.USER)
                .userStatus(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        userRepository.save(newUser);
        return "User registered successfully!";
    }

    public LoginResponse login(UserLoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        String accessToken = jwtProvider.generateToken(user.getEmail(), user.getUserId(), user.getUserRole());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getUserId());

        return LoginResponse.builder()
                .message("Login successful!")
                .email(user.getEmail())
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .expiresIn(jwtProvider.getAccessTokenExpirationTime())
                .build();
    }

    @Transactional
    public TokenRefreshResponse refreshToken(TokenRefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String accessToken = jwtProvider.generateToken(user.getEmail(), user.getUserId(), user.getUserRole());
                    // Rotate refresh token (optional but recommended for security)
                    RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(user.getUserId());
                    return TokenRefreshResponse.builder()
                            .accessToken(accessToken)
                            .refreshToken(newRefreshToken.getToken())
                            .tokenType("Bearer")
                            .build();
                })
                .orElseThrow(() -> new RuntimeException("Refresh token is not in database!"));
    }

    @Transactional
    public void updateRole(Long userId, UserRole newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
        user.setUserRole(newRole);
        userRepository.save(user);
    }

    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(user -> new UserDTO(
                        user.getUserId(),
                        user.getUsername(),
                        user.getEmail(),
                        user.getUserRole()
                ))
                .toList();
    }
}
