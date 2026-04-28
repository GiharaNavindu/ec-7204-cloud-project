package com.gihara.bidservice.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gihara.bidservice.dto.AuctionResponse;
import com.gihara.bidservice.dto.BidRequest;
import com.gihara.bidservice.dto.BidResponse;
import com.gihara.bidservice.entity.Bid;
import com.gihara.bidservice.entity.BidStatus;
import com.gihara.bidservice.entity.OutboxEvent;
import com.gihara.bidservice.event.BidPlacedEvent;
import com.gihara.bidservice.repository.BidRepository;
import com.gihara.bidservice.repository.OutboxRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BidService {

    private final BidRepository bidRepository;
    private final RestTemplate restTemplate;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.auction-service.url:http://localhost:8082}")
    private String auctionServiceUrl;

    @Transactional
    public BidResponse placeBid(BidRequest request, String userEmail, Long userId) {

        // Step 1 — check auction exists and is IN_PROG via HTTP
        AuctionResponse auction = getAuction(request.getAuctionId());
        if (!"IN_PROG".equals(auction.getStatus())) {
            throw new RuntimeException("Auction is not open for bidding. Current status: " + auction.getStatus());
        }

        // Step 2 — check bid is higher than current highest bid
        Optional<Bid> currentHighest = bidRepository
                .findTopByAuctionIdOrderByAmountDesc(request.getAuctionId());

        if (currentHighest.isPresent()) {
            BigDecimal highestAmount = currentHighest.get().getAmount();
            if (request.getAmount().compareTo(highestAmount) <= 0) {
                throw new RuntimeException(
                    "Bid amount must be higher than current highest bid of " + highestAmount
                );
            }
        }

        // Step 3 — save the bid
        Bid bid = Bid.builder()
                .auctionId(request.getAuctionId())
                .userId(userId)
                .userEmail(userEmail)
                .amount(request.getAmount())
                .status(BidStatus.ACTIVE)
                .build();

        Bid saved = bidRepository.save(bid);
        log.info("Bid placed: user={} auction={} amount={}", userEmail, request.getAuctionId(), request.getAmount());

        // Step 4 — Save event to Outbox
        BidPlacedEvent event = BidPlacedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .bidId(saved.getId())
                .auctionId(saved.getAuctionId())
                .userId(saved.getUserId())
                .userEmail(saved.getUserEmail())
                .amount(saved.getAmount())
                .placedAt(saved.getPlacedAt())
                .previousHighestBidId(getPreviousHighestBidId(currentHighest, userId))
                .previousHighestBidderUserId(getPreviousHighestBidderUserId(currentHighest, userId))
                .previousHighestBidderEmail(getPreviousHighestBidderEmail(currentHighest, userId))
                .previousHighestAmount(getPreviousHighestAmount(currentHighest, userId))
                .build();

        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .timestamp(LocalDateTime.now())
                    .aggregateId(saved.getId().toString())
                    .type("BID_PLACED")
                    .payload(payload)
                    .processed(false)
                    .build();
            
            outboxRepository.save(outboxEvent);
            log.info("BidPlacedEvent saved to outbox for auction={}", request.getAuctionId());
        } catch (Exception ex) {
            log.error("Failed to save BidPlacedEvent to outbox: {}", ex.getMessage());
            throw new RuntimeException("Could not process bid due to internal error");
        }

        return mapToResponse(saved);
    }

    public List<BidResponse> getBidsForAuction(Long auctionId) {
        return bidRepository.findByAuctionId(auctionId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<BidResponse> getBidsForUser(Long userId) {
        return bidRepository.findByUserId(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // HTTP call to auction-service via Eureka
    private AuctionResponse getAuction(Long auctionId) {
        try {
            return restTemplate.getForObject(
                auctionServiceUrl + "/api/auctions/" + auctionId,
                AuctionResponse.class
            );
        } catch (Exception e) {
            throw new RuntimeException("Could not reach auction-service. Is it running?");
        }
    }

    private BidResponse mapToResponse(Bid bid) {
        return BidResponse.builder()
                .id(bid.getId())
                .auctionId(bid.getAuctionId())
                .userId(bid.getUserId())
                .userEmail(bid.getUserEmail())
                .amount(bid.getAmount())
                .status(bid.getStatus())
                .placedAt(bid.getPlacedAt())
                .build();
    }

    private Long getPreviousHighestBidId(Optional<Bid> currentHighest, Long newBidUserId) {
        return currentHighest
                .filter(existingBid -> !existingBid.getUserId().equals(newBidUserId))
                .map(Bid::getId)
                .orElse(null);
    }

    private Long getPreviousHighestBidderUserId(Optional<Bid> currentHighest, Long newBidUserId) {
        return currentHighest
                .filter(existingBid -> !existingBid.getUserId().equals(newBidUserId))
                .map(Bid::getUserId)
                .orElse(null);
    }

    private String getPreviousHighestBidderEmail(Optional<Bid> currentHighest, Long newBidUserId) {
        return currentHighest
                .filter(existingBid -> !existingBid.getUserId().equals(newBidUserId))
                .map(Bid::getUserEmail)
                .orElse(null);
    }

    private BigDecimal getPreviousHighestAmount(Optional<Bid> currentHighest, Long newBidUserId) {
        return currentHighest
                .filter(existingBid -> !existingBid.getUserId().equals(newBidUserId))
                .map(Bid::getAmount)
                .orElse(null);
    }
}
