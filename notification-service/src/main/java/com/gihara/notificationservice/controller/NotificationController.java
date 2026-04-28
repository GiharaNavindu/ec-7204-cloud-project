package com.gihara.notificationservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gihara.notificationservice.dto.CreateNotificationRequest;
import com.gihara.notificationservice.dto.NotificationPageResponse;
import com.gihara.notificationservice.dto.NotificationReadAllResponse;
import com.gihara.notificationservice.dto.NotificationResponse;
import com.gihara.notificationservice.dto.UnreadCountResponse;
import com.gihara.notificationservice.exception.NotificationAccessDeniedException;
import com.gihara.notificationservice.service.NotificationService;
import com.gihara.notificationservice.util.JwtProvider;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final String ADMIN_ROLE = "ADMIN";

    private final NotificationService notificationService;
    private final JwtProvider jwtProvider;

    @PostMapping
    public ResponseEntity<NotificationResponse> createNotification(
            @Valid @RequestBody CreateNotificationRequest request,
            HttpServletRequest httpRequest) {

        authorizeUserAccess(request.getUserId(), httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(notificationService.createNotification(request));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<NotificationPageResponse> getUserNotifications(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {

        authorizeUserAccess(userId, httpRequest);
        return ResponseEntity.ok(notificationService.getUserNotifications(userId, page, size));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {

        Long requesterUserId = getRequesterUserId(httpRequest);
        return ResponseEntity.ok(notificationService.markAsRead(id, requesterUserId));
    }

    @PatchMapping("/user/{userId}/read-all")
    public ResponseEntity<NotificationReadAllResponse> markAllAsRead(
            @PathVariable Long userId,
            HttpServletRequest httpRequest) {

        authorizeUserAccess(userId, httpRequest);
        return ResponseEntity.ok(notificationService.markAllAsRead(userId));
    }

    @GetMapping("/user/{userId}/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @PathVariable Long userId,
            HttpServletRequest httpRequest) {

        authorizeUserAccess(userId, httpRequest);
        return ResponseEntity.ok(notificationService.getUnreadCount(userId));
    }

    private void authorizeUserAccess(Long targetUserId, HttpServletRequest request) {
        Long requesterUserId = getRequesterUserId(request);
        String role = getRequesterRole(request);

        if (ADMIN_ROLE.equals(role)) {
            return;
        }

        if (!targetUserId.equals(requesterUserId)) {
            throw new NotificationAccessDeniedException("You are not allowed to access notifications for this user");
        }
    }

    private Long getRequesterUserId(HttpServletRequest request) {
        return jwtProvider.getUserIdFromToken(extractToken(request));
    }

    private String getRequesterRole(HttpServletRequest request) {
        return jwtProvider.getRoleFromToken(extractToken(request));
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        throw new NotificationAccessDeniedException("No token found in request");
    }
}
