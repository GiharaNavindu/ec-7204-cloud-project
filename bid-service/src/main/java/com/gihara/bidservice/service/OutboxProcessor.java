package com.gihara.bidservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gihara.bidservice.entity.OutboxEvent;
import com.gihara.bidservice.repository.OutboxRepository;
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
    private final ObjectMapper objectMapper;

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.routing-key}")
    private String routingKey;

    @Scheduled(fixedDelay = 5000) // Poll every 5 seconds
    @Transactional
    public void processOutbox() {
        List<OutboxEvent> events = outboxRepository.findByProcessedFalseOrderByTimestampAsc();
        
        for (OutboxEvent event : events) {
            try {
                // Determine routing key based on event type if needed, 
                // for now using the default routing key from config
                rabbitTemplate.convertAndSend(exchange, routingKey, event.getPayload());
                
                event.setProcessed(true);
                outboxRepository.save(event);
                log.info("Successfully published event from outbox: ID={}, Type={}", event.getId(), event.getType());
            } catch (Exception e) {
                log.error("Failed to publish event from outbox: ID={}, Error={}", event.getId(), e.getMessage());
                // Event remains unprocessed for the next cycle
            }
        }
    }
}
