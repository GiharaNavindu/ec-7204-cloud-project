package com.gihara.userservice.service;

import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.gihara.userservice.dto.LoginResponse;
import com.gihara.userservice.dto.UserLoginRequest;
import com.gihara.userservice.dto.UserRegistrationRequest;
import com.gihara.userservice.entity.User;
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
                .orElseThrow(() -> new RuntimeException("User not found with email: " + request.email()));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new RuntimeException("Invalid password!");
        }

        String token = jwtProvider.generateToken(user.getEmail(), user.getUserRole());
        
        return LoginResponse.builder()
                .message("Login successful!")
                .email(user.getEmail())
                .token(token)
                .expiresIn(jwtProvider.getExpirationTime())
                .build();
    }
}