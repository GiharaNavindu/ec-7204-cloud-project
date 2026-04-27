package com.gihara.bidservice.dto;

import com.gihara.bidservice.entity.BidStatus;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class BidResponse {
    private Long id;
    private Long auctionId;
    private Long userId;
    private String userEmail;
    private BigDecimal amount;
    private BidStatus status;
    private LocalDateTime placedAt;
}