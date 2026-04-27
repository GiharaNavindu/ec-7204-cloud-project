package com.gihara.bidservice.controller;

import com.gihara.bidservice.dto.BidRequest;
import com.gihara.bidservice.dto.BidResponse;
import com.gihara.bidservice.service.BidService;
import com.gihara.bidservice.util.JwtProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bids")
@RequiredArgsConstructor
public class BidController {

    private final BidService bidService;
    private final JwtProvider jwtProvider;

    // POST /api/bids — place a bid
    @PostMapping
    public ResponseEntity<BidResponse> placeBid(
            @Valid @RequestBody BidRequest request,
            HttpServletRequest httpRequest) {

        // extract user info from JWT — no need to call user-service
        String token = extractToken(httpRequest);
        String email = jwtProvider.getEmailFromToken(token);
        Long userId = jwtProvider.getUserIdFromToken(token);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(bidService.placeBid(request, email, userId));
    }

    // GET /api/bids/auction/{auctionId} — all bids for an auction
    @GetMapping("/auction/{auctionId}")
    public ResponseEntity<List<BidResponse>> getBidsForAuction(@PathVariable Long auctionId) {
        return ResponseEntity.ok(bidService.getBidsForAuction(auctionId));
    }

    // GET /api/bids/user/{userId} — all bids by a user
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<BidResponse>> getBidsForUser(@PathVariable Long userId) {
        return ResponseEntity.ok(bidService.getBidsForUser(userId));
    }

    @GetMapping("/status")
    public String status() {
        return "Bid Service is up and running!";
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        throw new RuntimeException("No token found in request");
    }
}