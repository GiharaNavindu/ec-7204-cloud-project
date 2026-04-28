package com.gihara.auctionservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gihara.auctionservice.entity.OutboxEvent;
import com.gihara.auctionservice.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public AuctionResponse createAuction(AuctionRequest request) {
        Auction auction = Auction.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .createdByUserId(request.getCreatedByUserId())
                .status(AuctionStatus.OPENED)
                .build();

        return mapToResponse(auctionRepository.save(auction));
    }

    public AuctionResponse getAuctionById(Long id) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Auction not found: " + id));
        return mapToResponse(auction);
    }

    public List<AuctionResponse> getAllAuctions() {
        return auctionRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // used by bid-service later via HTTP to validate auction state
    public List<AuctionResponse> getAuctionsByStatus(AuctionStatus status) {
        return auctionRepository.findByStatus(status)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public AuctionResponse updateStatus(Long id, AuctionStatus newStatus) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Auction not found: " + id));
        
        auction.setStatus(newStatus);
        Auction saved = auctionRepository.save(auction);

        if (newStatus == AuctionStatus.CLOSED) {
            saveOutboxEvent(saved, "AUCTION_CLOSED");
        } else if (newStatus == AuctionStatus.IN_PROG) {
            saveOutboxEvent(saved, "AUCTION_STARTED");
        }

        return mapToResponse(saved);
    }

    private void saveOutboxEvent(Auction auction, String type) {
        try {
            AuctionResponse response = mapToResponse(auction);
            String payload = objectMapper.writeValueAsString(response);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .timestamp(LocalDateTime.now())
                    .aggregateId(auction.getId().toString())
                    .type(type)
                    .payload(payload)
                    .processed(false)
                    .build();

            outboxRepository.save(outboxEvent);
            log.info("Saved {} event to outbox for auction {}", type, auction.getId());
        } catch (Exception e) {
            log.error("Failed to save {} event to outbox: {}", type, e.getMessage());
            throw new RuntimeException("Database error occurred while closing auction");
        }
    }

    private AuctionResponse mapToResponse(Auction auction) {
        return AuctionResponse.builder()
                .id(auction.getId())
                .title(auction.getTitle())
                .description(auction.getDescription())
                .status(auction.getStatus())
                .startTime(auction.getStartTime())
                .endTime(auction.getEndTime())
                .createdByUserId(auction.getCreatedByUserId())
                .createdAt(auction.getCreatedAt())
                .build();
    }
}