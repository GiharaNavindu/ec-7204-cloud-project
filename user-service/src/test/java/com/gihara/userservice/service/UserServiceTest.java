package com.gihara.userservice.service;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.gihara.userservice.dto.UserLoginRequest;
import com.gihara.userservice.dto.UserRegistrationRequest;
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

        assertTrue(ex.getMessage().contains("User not found with email"));
    }

    @Test
    void login_shouldThrow_whenPasswordMismatch() {
        UserLoginRequest request = new UserLoginRequest("jane@example.com", "bad-pass");
        User user = User.builder().email("jane@example.com").password("encoded-pass").build();

        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad-pass", "encoded-pass")).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.login(request));

        assertEquals("Invalid password!", ex.getMessage());
    }

    @Test
    void login_shouldReturnTokenResponse_whenCredentialsAreValid() {
        UserLoginRequest request = new UserLoginRequest("jane@example.com", "plain-pass");
        User user = User.builder()
            .email("jane@example.com")
            .password("encoded-pass")
            .userRole(UserRole.USER)
            .build();

        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("plain-pass", "encoded-pass")).thenReturn(true);
        when(jwtProvider.generateToken("jane@example.com", null, UserRole.USER)).thenReturn("jwt-token");
        when(jwtProvider.getExpirationTime()).thenReturn(86400000L);

        LoginResponse response = userService.login(request);

        assertEquals("Login successful!", response.getMessage());
        assertEquals("jane@example.com", response.getEmail());
        assertEquals("jwt-token", response.getToken());
        assertEquals(86400000L, response.getExpiresIn());
    }
}
