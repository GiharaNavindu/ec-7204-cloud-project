package com.gihara.auctionservice.dto;

import com.gihara.auctionservice.entity.AuctionStatus;
import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;

@Data
@Builder
public class AuctionResponse {
    private Long id;
    private String title;
    private String description;
    private AuctionStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long createdByUserId;
    private LocalDateTime createdAt;
}