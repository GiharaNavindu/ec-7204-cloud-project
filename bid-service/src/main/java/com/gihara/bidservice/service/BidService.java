package com.gihara.bidservice.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.gihara.bidservice.dto.AuctionResponse;
import com.gihara.bidservice.dto.BidRequest;
import com.gihara.bidservice.dto.BidResponse;
import com.gihara.bidservice.entity.Bid;
import com.gihara.bidservice.entity.BidStatus;
import com.gihara.bidservice.event.BidPlacedEvent;
import com.gihara.bidservice.repository.BidRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BidService {

    private final BidRepository bidRepository;
    private final RestTemplate restTemplate;       // calls auction-service
    private final RabbitTemplate rabbitTemplate;   // publishes events

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.routing-key}")
    private String routingKey;

    @Value("${app.auction-service.url:http://localhost:8082}")
    private String auctionServiceUrl;

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

        // Step 4 — publish event to RabbitMQ
        // auction-service will listen to this and update its highest bid
        BidPlacedEvent event = BidPlacedEvent.builder()
                .bidId(saved.getId())
                .auctionId(saved.getAuctionId())
                .userId(saved.getUserId())
                .userEmail(saved.getUserEmail())
                .amount(saved.getAmount())
                .placedAt(saved.getPlacedAt())
                .build();

        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, event);
            log.info("BidPlacedEvent published to RabbitMQ for auction={}", request.getAuctionId());
        } catch (Exception ex) {
            // Do not fail bid placement because event publishing is eventual-consistency infrastructure.
            log.warn("Bid persisted but event publish failed for auction={}: {}", request.getAuctionId(), ex.getMessage());
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
}