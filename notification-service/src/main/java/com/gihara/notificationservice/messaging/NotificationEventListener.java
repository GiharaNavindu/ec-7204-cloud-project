package com.gihara.notificationservice.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.gihara.notificationservice.event.BidPlacedEvent;
import com.gihara.notificationservice.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @RabbitListener(queues = "${rabbitmq.notification.queue}")
    public void handleBidPlacedEvent(BidPlacedEvent event) {
        log.info("Received BidPlacedEvent eventId={} auctionId={} bidId={}",
                event.getEventId(), event.getAuctionId(), event.getBidId());
        notificationService.createOutbidNotification(event);
    }
}
