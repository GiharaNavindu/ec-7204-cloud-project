package com.gihara.notificationservice.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.gihara.notificationservice.entity.Notification;
import com.gihara.notificationservice.entity.NotificationChannel;
import com.gihara.notificationservice.entity.NotificationStatus;
import com.gihara.notificationservice.entity.NotificationType;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void markAllAsReadByUserId_shouldUpdateOnlyUnreadNotificationsForGivenUser() {
        notificationRepository.save(Notification.builder()
                .userId(10L)
                .type(NotificationType.GENERIC)
                .title("One")
                .message("First")
                .status(NotificationStatus.UNREAD)
                .channel(NotificationChannel.IN_APP)
                .build());

        notificationRepository.save(Notification.builder()
                .userId(10L)
                .type(NotificationType.GENERIC)
                .title("Two")
                .message("Second")
                .status(NotificationStatus.UNREAD)
                .channel(NotificationChannel.IN_APP)
                .build());

        notificationRepository.save(Notification.builder()
                .userId(10L)
                .type(NotificationType.GENERIC)
                .title("Three")
                .message("Third")
                .status(NotificationStatus.READ)
                .channel(NotificationChannel.IN_APP)
                .readAt(LocalDateTime.of(2026, 4, 28, 8, 0))
                .build());

        notificationRepository.save(Notification.builder()
                .userId(11L)
                .type(NotificationType.GENERIC)
                .title("Other user")
                .message("Other")
                .status(NotificationStatus.UNREAD)
                .channel(NotificationChannel.IN_APP)
                .build());

        int updated = notificationRepository.markAllAsReadByUserId(10L, LocalDateTime.of(2026, 4, 28, 12, 0));

        assertEquals(2, updated);
        assertEquals(0L, notificationRepository.countByUserIdAndStatus(10L, NotificationStatus.UNREAD));
        assertEquals(1L, notificationRepository.countByUserIdAndStatus(11L, NotificationStatus.UNREAD));
    }
}
