package com.gihara.notificationservice.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidPlacedEvent {

    private String eventId;
    private Long bidId;
    private Long auctionId;
    private Long userId;
    private String userEmail;
    private BigDecimal amount;
    private LocalDateTime placedAt;
    private Long previousHighestBidId;
    private Long previousHighestBidderUserId;
    private String previousHighestBidderEmail;
    private BigDecimal previousHighestAmount;
}
