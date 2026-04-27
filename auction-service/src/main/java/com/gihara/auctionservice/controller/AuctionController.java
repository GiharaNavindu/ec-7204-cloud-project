package com.gihara.auctionservice.controller;

import com.gihara.auctionservice.dto.AuctionRequest;
import com.gihara.auctionservice.dto.AuctionResponse;
import com.gihara.auctionservice.entity.AuctionStatus;
import com.gihara.auctionservice.service.AuctionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController                    // marks this as a REST controller
@RequestMapping("/api/auctions")   // base URL for all endpoints in this class
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionService auctionService;

    // POST /api/auctions
    @PostMapping
    public ResponseEntity<AuctionResponse> createAuction(
            @Valid @RequestBody AuctionRequest request) {   // @Valid triggers DTO validation
        return ResponseEntity
                .status(HttpStatus.CREATED)   // 201
                .body(auctionService.createAuction(request));
    }

    // GET /api/auctions/{id}
    @GetMapping("/{id}")
    public ResponseEntity<AuctionResponse> getAuction(@PathVariable Long id) {
        return ResponseEntity.ok(auctionService.getAuctionById(id));
    }

    // GET /api/auctions
    @GetMapping
    public ResponseEntity<List<AuctionResponse>> getAllAuctions() {
        return ResponseEntity.ok(auctionService.getAllAuctions());
    }

    // PATCH /api/auctions/{id}/status?status=IN_PROG
    @PatchMapping("/{id}/status")
    public ResponseEntity<AuctionResponse> updateStatus(
            @PathVariable Long id,
            @RequestParam AuctionStatus status) {
        return ResponseEntity.ok(auctionService.updateStatus(id, status));
    }
}