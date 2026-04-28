package com.gihara.notificationservice.dto;

import java.time.LocalDateTime;

import com.gihara.notificationservice.entity.NotificationChannel;
import com.gihara.notificationservice.entity.NotificationStatus;
import com.gihara.notificationservice.entity.NotificationType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private Long id;
    private Long userId;
    private NotificationType type;
    private String title;
    private String message;
    private NotificationStatus status;
    private NotificationChannel channel;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
    private Long auctionId;
    private Long bidId;
    private String referenceId;
    private String metadata;
}
