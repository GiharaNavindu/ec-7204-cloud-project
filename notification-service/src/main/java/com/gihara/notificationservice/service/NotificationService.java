package com.gihara.notificationservice.service;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gihara.notificationservice.dto.CreateNotificationRequest;
import com.gihara.notificationservice.dto.NotificationPageResponse;
import com.gihara.notificationservice.dto.NotificationReadAllResponse;
import com.gihara.notificationservice.dto.NotificationResponse;
import com.gihara.notificationservice.dto.UnreadCountResponse;
import com.gihara.notificationservice.entity.Notification;
import com.gihara.notificationservice.entity.NotificationChannel;
import com.gihara.notificationservice.entity.NotificationStatus;
import com.gihara.notificationservice.exception.NotificationAccessDeniedException;
import com.gihara.notificationservice.exception.NotificationNotFoundException;
import com.gihara.notificationservice.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public NotificationResponse createNotification(CreateNotificationRequest request) {
        Notification notification = Notification.builder()
                .userId(request.getUserId())
                .type(request.getType())
                .title(request.getTitle())
                .message(request.getMessage())
                .channel(request.getChannel() != null ? request.getChannel() : NotificationChannel.IN_APP)
                .status(NotificationStatus.UNREAD)
                .auctionId(request.getAuctionId())
                .bidId(request.getBidId())
                .referenceId(request.getReferenceId())
                .metadata(request.getMetadata())
                .build();

        return mapToResponse(notificationRepository.save(notification));
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse getUserNotifications(Long userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Notification> notificationPage = notificationRepository.findByUserId(userId, pageRequest);

        return NotificationPageResponse.builder()
                .content(notificationPage.getContent().stream().map(this::mapToResponse).toList())
                .pageNumber(notificationPage.getNumber())
                .pageSize(notificationPage.getSize())
                .totalElements(notificationPage.getTotalElements())
                .totalPages(notificationPage.getTotalPages())
                .last(notificationPage.isLast())
                .build();
    }

    @Transactional
    public NotificationResponse markAsRead(Long notificationId, Long requesterUserId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException("Notification not found: " + notificationId));

        if (!notification.getUserId().equals(requesterUserId)) {
            throw new NotificationAccessDeniedException("You are not allowed to update this notification");
        }

        if (notification.getStatus() == NotificationStatus.UNREAD) {
            notification.setStatus(NotificationStatus.READ);
            notification.setReadAt(LocalDateTime.now());
        }

        return mapToResponse(notificationRepository.save(notification));
    }

    @Transactional
    public NotificationReadAllResponse markAllAsRead(Long userId) {
        int updatedCount = notificationRepository.markAllAsReadByUserId(userId, LocalDateTime.now());
        return NotificationReadAllResponse.builder()
                .userId(userId)
                .updatedCount(updatedCount)
                .build();
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(Long userId) {
        long unreadCount = notificationRepository.countByUserIdAndStatus(userId, NotificationStatus.UNREAD);
        return UnreadCountResponse.builder()
                .userId(userId)
                .unreadCount(unreadCount)
                .build();
    }

    private NotificationResponse mapToResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .userId(notification.getUserId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .status(notification.getStatus())
                .channel(notification.getChannel())
                .createdAt(notification.getCreatedAt())
                .readAt(notification.getReadAt())
                .auctionId(notification.getAuctionId())
                .bidId(notification.getBidId())
                .referenceId(notification.getReferenceId())
                .metadata(notification.getMetadata())
                .build();
    }
}
