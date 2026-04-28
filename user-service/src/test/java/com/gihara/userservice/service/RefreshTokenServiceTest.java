package com.gihara.userservice.service;

import com.gihara.userservice.entity.RefreshToken;
import com.gihara.userservice.entity.User;
import com.gihara.userservice.repository.RefreshTokenRepository;
import com.gihara.userservice.repository.UserRepository;
import com.gihara.userservice.util.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User user;
    private Long userId = 1L;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .userId(userId)
                .email("test@example.com")
                .build();
    }

    @Test
    void createRefreshToken_shouldDeleteExistingToken_whenTokenExists() {
        // Arrange
        RefreshToken existingToken = RefreshToken.builder()
                .id(100L)
                .user(user)
                .token("old-token")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByUser(user)).thenReturn(Optional.of(existingToken));
        when(jwtProvider.getRefreshTokenExpirationTime()).thenReturn(3600000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        RefreshToken result = refreshTokenService.createRefreshToken(userId);

        // Assert
        assertNotNull(result);
        verify(refreshTokenRepository).delete(existingToken);
        verify(refreshTokenRepository).flush();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void createRefreshToken_shouldNotDelete_whenTokenDoesNotExist() {
        // Arrange
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByUser(user)).thenReturn(Optional.empty());
        when(jwtProvider.getRefreshTokenExpirationTime()).thenReturn(3600000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        RefreshToken result = refreshTokenService.createRefreshToken(userId);

        // Assert
        assertNotNull(result);
        verify(refreshTokenRepository, never()).delete(any(RefreshToken.class));
        verify(refreshTokenRepository, never()).flush();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }
}
