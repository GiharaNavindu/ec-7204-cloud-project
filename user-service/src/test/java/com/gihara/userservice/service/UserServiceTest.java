package com.gihara.userservice.service;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.gihara.userservice.dto.LoginResponse;
import com.gihara.userservice.dto.TokenRefreshRequest;
import com.gihara.userservice.dto.TokenRefreshResponse;
import com.gihara.userservice.dto.UserLoginRequest;
import com.gihara.userservice.dto.UserRegistrationRequest;
import com.gihara.userservice.entity.RefreshToken;
import com.gihara.userservice.entity.User;
import com.gihara.userservice.enums.UserRole;
import com.gihara.userservice.repository.UserRepository;
import com.gihara.userservice.util.JwtProvider;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private UserService userService;

    @Test
    void registerUser_shouldSaveUser_whenEmailIsUnique() {
        UserRegistrationRequest request = new UserRegistrationRequest("Jane", "jane@example.com", "plain-pass");
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plain-pass")).thenReturn("encoded-pass");

        String result = userService.registerUser(request);

        assertEquals("User registered successfully!", result);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals("Jane", saved.getUsername());
        assertEquals("jane@example.com", saved.getEmail());
        assertEquals("encoded-pass", saved.getPassword());
        assertEquals(UserRole.USER, saved.getUserRole());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void registerUser_shouldThrow_whenEmailAlreadyExists() {
        UserRegistrationRequest request = new UserRegistrationRequest("Jane", "jane@example.com", "plain-pass");
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.registerUser(request));

        assertEquals("Email is already in use!", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_shouldThrow_whenUserNotFound() {
        UserLoginRequest request = new UserLoginRequest("missing@example.com", "plain-pass");
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.login(request));

        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void login_shouldThrow_whenPasswordMismatch() {
        UserLoginRequest request = new UserLoginRequest("jane@example.com", "bad-pass");
        User user = User.builder().email("jane@example.com").password("encoded-pass").build();

        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad-pass", "encoded-pass")).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.login(request));

        assertEquals("Invalid credentials", ex.getMessage());
    }

    @Test
    void login_shouldReturnTokenResponse_whenCredentialsAreValid() {
        UserLoginRequest request = new UserLoginRequest("jane@example.com", "plain-pass");
        User user = User.builder()
            .userId(1L)
            .email("jane@example.com")
            .password("encoded-pass")
            .userRole(UserRole.USER)
            .build();
        RefreshToken refreshToken = RefreshToken.builder().token("refresh-token").build();

        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("plain-pass", "encoded-pass")).thenReturn(true);
        when(jwtProvider.generateToken("jane@example.com", 1L, UserRole.USER)).thenReturn("jwt-token");
        when(jwtProvider.getAccessTokenExpirationTime()).thenReturn(86400000L);
        when(refreshTokenService.createRefreshToken(1L)).thenReturn(refreshToken);

        LoginResponse response = userService.login(request);

        assertEquals("Login successful!", response.getMessage());
        assertEquals("jane@example.com", response.getEmail());
        assertEquals("jwt-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals(86400000L, response.getExpiresIn());
    }

    @Test
    void refreshToken_shouldReturnRotatedTokens_whenRefreshTokenIsValid() {
        User user = User.builder()
                .userId(1L)
                .email("jane@example.com")
                .userRole(UserRole.USER)
                .build();
        RefreshToken refreshToken = RefreshToken.builder()
                .token("refresh-token")
                .user(user)
                .expiryDate(Instant.now().plusSeconds(60))
                .build();
        RefreshToken rotatedRefreshToken = RefreshToken.builder().token("new-refresh-token").build();

        when(refreshTokenService.findByToken("refresh-token")).thenReturn(Optional.of(refreshToken));
        when(refreshTokenService.verifyExpiration(refreshToken)).thenReturn(refreshToken);
        when(jwtProvider.generateToken("jane@example.com", 1L, UserRole.USER)).thenReturn("new-access-token");
        when(refreshTokenService.createRefreshToken(1L)).thenReturn(rotatedRefreshToken);

        TokenRefreshResponse response = userService.refreshToken(new TokenRefreshRequest("refresh-token"));

        assertEquals("new-access-token", response.getAccessToken());
        assertEquals("new-refresh-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
    }

    @Test
    void refreshToken_shouldThrow_whenTokenDoesNotExist() {
        when(refreshTokenService.findByToken("missing-token")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.refreshToken(new TokenRefreshRequest("missing-token")));

        assertEquals("Refresh token is not in database!", ex.getMessage());
    }
}
