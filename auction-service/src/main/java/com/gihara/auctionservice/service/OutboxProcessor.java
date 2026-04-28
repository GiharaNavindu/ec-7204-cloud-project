package com.gihara.auctionservice.service;

import com.gihara.auctionservice.entity.OutboxEvent;
import com.gihara.auctionservice.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxProcessor {

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange:auction.exchange}")
    private String exchange;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processOutbox() {
        List<OutboxEvent> events = outboxRepository.findByProcessedFalseOrderByTimestampAsc();
        
        for (OutboxEvent event : events) {
            try {
                // Use event type as routing key or a default one
                String routingKey = event.getType().toLowerCase();
                
                // Payload is already a JSON string in the database
                rabbitTemplate.convertAndSend(exchange, routingKey, event.getPayload());
                
                event.setProcessed(true);
                outboxRepository.save(event);
                log.info("Successfully published auction event: ID={}, Type={}", event.getId(), event.getType());
            } catch (Exception e) {
                log.error("Failed to publish auction event: ID={}, Error={}", event.getId(), e.getMessage());
            }
        }
    }
}
