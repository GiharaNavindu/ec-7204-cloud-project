package com.gihara.bidservice.dto;

import lombok.Data;

// local DTO - only fields bid-service needs from auction-service response
@Data
public class AuctionResponse {
    private Long id;
    private String status;    // we check if this is "IN_PROG"
}