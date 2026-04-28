package com.gihara.notificationservice.dto;

import com.gihara.notificationservice.entity.NotificationChannel;
import com.gihara.notificationservice.entity.NotificationType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class CreateNotificationRequest {

    @NotNull(message = "userId is required")
    private Long userId;

    @NotNull(message = "type is required")
    private NotificationType type;

    @NotBlank(message = "title is required")
    @Size(max = 150, message = "title must be at most 150 characters")
    private String title;

    @NotBlank(message = "message is required")
    @Size(max = 1000, message = "message must be at most 1000 characters")
    private String message;

    private NotificationChannel channel;

    private Long auctionId;

    private Long bidId;

    @Size(max = 100, message = "referenceId must be at most 100 characters")
    private String referenceId;

    @Size(max = 2000, message = "metadata must be at most 2000 characters")
    private String metadata;
}
