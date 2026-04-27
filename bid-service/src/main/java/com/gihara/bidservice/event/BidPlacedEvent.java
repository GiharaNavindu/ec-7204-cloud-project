package com.gihara.bidservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidPlacedEvent {
    private Long bidId;
    private Long auctionId;
    private Long userId;
    private String userEmail;
    private BigDecimal amount;
    private LocalDateTime placedAt;
}