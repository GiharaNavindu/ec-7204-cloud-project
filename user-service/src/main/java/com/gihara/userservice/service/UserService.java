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

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        String token = jwtProvider.generateToken(user.getEmail(), user.getId(), user.getUserRole().name());

        return LoginResponse.builder()
                .message("Login successful!")
                .email(user.getEmail())
                .token(token)
                .expiresIn(jwtProvider.getExpirationTime())
                .build();
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
                        user.getId(),
                        user.getUsername(),
                        user.getEmail(),
                        user.getUserRole()
                ))
                .toList();
    }
    }