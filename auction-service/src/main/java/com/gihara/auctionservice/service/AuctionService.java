package com.gihara.auctionservice.service;

import com.gihara.auctionservice.dto.AuctionRequest;
import com.gihara.auctionservice.dto.AuctionResponse;
import com.gihara.auctionservice.entity.Auction;
import com.gihara.auctionservice.entity.AuctionStatus;
import com.gihara.auctionservice.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuctionService {

    private final AuctionRepository auctionRepository;

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

    public AuctionResponse updateStatus(Long id, AuctionStatus newStatus) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Auction not found: " + id));
        auction.setStatus(newStatus);
        return mapToResponse(auctionRepository.save(auction));
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