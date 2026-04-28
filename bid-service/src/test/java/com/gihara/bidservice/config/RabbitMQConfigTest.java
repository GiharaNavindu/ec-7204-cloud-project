package com.gihara.bidservice.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import com.gihara.bidservice.event.BidPlacedEvent;

class RabbitMQConfigTest {

    private final RabbitMQConfig rabbitMQConfig = new RabbitMQConfig();

    @Test
    void messageConverter_shouldSerializeBidPlacedEventWithJavaTimeFields() {
        Jackson2JsonMessageConverter converter = rabbitMQConfig.messageConverter();

        BidPlacedEvent event = BidPlacedEvent.builder()
                .eventId("evt-100")
                .bidId(5L)
                .auctionId(1L)
                .userId(2L)
                .userEmail("test@example.com")
                .amount(new BigDecimal("150.00"))
                .placedAt(LocalDateTime.of(2026, 4, 28, 12, 0))
                .previousHighestBidId(4L)
                .previousHighestBidderUserId(3L)
                .previousHighestBidderEmail("previous@example.com")
                .previousHighestAmount(new BigDecimal("120.00"))
                .build();

        var message = converter.toMessage(event, new MessageProperties());
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);

        assertNotNull(message);
        assertTrue(payload.contains("\"eventId\":\"evt-100\""));
        assertTrue(payload.contains("\"placedAt\":\"2026-04-28T12:00:00\""));
        assertTrue(payload.contains("\"previousHighestBidderUserId\":3"));
    }
}
