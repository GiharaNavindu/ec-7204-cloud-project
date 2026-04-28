package com.gihara.notificationservice.messaging;

import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.gihara.notificationservice.event.BidPlacedEvent;
import com.gihara.notificationservice.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationEventListener notificationEventListener;

    @Test
    void handleBidPlacedEvent_shouldDelegateToNotificationService() {
        BidPlacedEvent event = BidPlacedEvent.builder()
                .eventId("evt-9")
                .bidId(55L)
                .auctionId(12L)
                .userId(4L)
                .userEmail("new@example.com")
                .amount(new BigDecimal("250.00"))
                .placedAt(LocalDateTime.of(2026, 4, 28, 12, 30))
                .previousHighestBidderUserId(8L)
                .build();

        notificationEventListener.handleBidPlacedEvent(event);

        verify(notificationService).createOutbidNotification(event);
    }
}
