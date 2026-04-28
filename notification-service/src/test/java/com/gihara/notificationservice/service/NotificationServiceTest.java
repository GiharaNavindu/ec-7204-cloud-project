package com.gihara.notificationservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.gihara.notificationservice.dto.CreateNotificationRequest;
import com.gihara.notificationservice.dto.NotificationPageResponse;
import com.gihara.notificationservice.dto.NotificationReadAllResponse;
import com.gihara.notificationservice.dto.NotificationResponse;
import com.gihara.notificationservice.dto.UnreadCountResponse;
import com.gihara.notificationservice.entity.Notification;
import com.gihara.notificationservice.entity.NotificationChannel;
import com.gihara.notificationservice.entity.NotificationStatus;
import com.gihara.notificationservice.entity.NotificationType;
import com.gihara.notificationservice.exception.NotificationAccessDeniedException;
import com.gihara.notificationservice.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void createNotification_shouldPersistUnreadNotification() {
        CreateNotificationRequest request = CreateNotificationRequest.builder()
                .userId(7L)
                .type(NotificationType.AUCTION_WON)
                .title("Auction won")
                .message("You won auction #15")
                .auctionId(15L)
                .build();

        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(100L);
            notification.setCreatedAt(LocalDateTime.of(2026, 4, 28, 10, 0));
            return notification;
        });

        NotificationResponse response = notificationService.createNotification(request);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification savedNotification = captor.getValue();
        assertEquals(7L, savedNotification.getUserId());
        assertEquals(NotificationStatus.UNREAD, savedNotification.getStatus());
        assertEquals(NotificationChannel.IN_APP, savedNotification.getChannel());
        assertEquals(100L, response.getId());
        assertNotNull(response.getCreatedAt());
    }

    @Test
    void getUserNotifications_shouldReturnPagedNotifications() {
        Notification newest = Notification.builder()
                .id(2L)
                .userId(9L)
                .type(NotificationType.BID_OUTBID)
                .title("Outbid")
                .message("Another user placed a higher bid")
                .status(NotificationStatus.UNREAD)
                .channel(NotificationChannel.IN_APP)
                .createdAt(LocalDateTime.of(2026, 4, 28, 11, 0))
                .build();

        Notification older = Notification.builder()
                .id(1L)
                .userId(9L)
                .type(NotificationType.SYSTEM_ALERT)
                .title("Reminder")
                .message("Profile verification is pending")
                .status(NotificationStatus.READ)
                .channel(NotificationChannel.IN_APP)
                .createdAt(LocalDateTime.of(2026, 4, 27, 11, 0))
                .build();

        Page<Notification> page = new PageImpl<>(List.of(newest, older), PageRequest.of(0, 20), 2);
        when(notificationRepository.findByUserId(9L, PageRequest.of(0, 20, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))))
                .thenReturn(page);

        NotificationPageResponse response = notificationService.getUserNotifications(9L, 0, 20);

        assertEquals(2, response.getContent().size());
        assertEquals(2L, response.getContent().get(0).getId());
        assertEquals(2L, response.getTotalElements());
    }

    @Test
    void markAsRead_shouldSetStatusAndReadTimestamp() {
        Notification notification = Notification.builder()
                .id(5L)
                .userId(11L)
                .type(NotificationType.GENERIC)
                .title("Title")
                .message("Body")
                .status(NotificationStatus.UNREAD)
                .channel(NotificationChannel.IN_APP)
                .createdAt(LocalDateTime.of(2026, 4, 28, 8, 0))
                .build();

        when(notificationRepository.findById(5L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponse response = notificationService.markAsRead(5L, 11L);

        assertEquals(NotificationStatus.READ, response.getStatus());
        assertNotNull(response.getReadAt());
    }

    @Test
    void markAsRead_shouldRejectOtherUsers() {
        Notification notification = Notification.builder()
                .id(5L)
                .userId(12L)
                .type(NotificationType.GENERIC)
                .title("Title")
                .message("Body")
                .status(NotificationStatus.UNREAD)
                .channel(NotificationChannel.IN_APP)
                .build();

        when(notificationRepository.findById(5L)).thenReturn(Optional.of(notification));

        NotificationAccessDeniedException ex = assertThrows(NotificationAccessDeniedException.class,
                () -> notificationService.markAsRead(5L, 11L));

        assertEquals("You are not allowed to update this notification", ex.getMessage());
    }

    @Test
    void markAllAsRead_shouldReturnUpdatedCount() {
        when(notificationRepository.markAllAsReadByUserId(eq(4L), any(LocalDateTime.class))).thenReturn(3);

        NotificationReadAllResponse response = notificationService.markAllAsRead(4L);

        assertEquals(4L, response.getUserId());
        assertEquals(3, response.getUpdatedCount());
    }

    @Test
    void getUnreadCount_shouldReturnRepositoryCount() {
        when(notificationRepository.countByUserIdAndStatus(15L, NotificationStatus.UNREAD)).thenReturn(6L);

        UnreadCountResponse response = notificationService.getUnreadCount(15L);

        assertEquals(15L, response.getUserId());
        assertEquals(6L, response.getUnreadCount());
    }
}
