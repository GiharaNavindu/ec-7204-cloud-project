package com.gihara.bidservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bids")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long auctionId;       // reference to auction-service (just an ID)

    @Column(nullable = false)
    private Long userId;          // reference to user-service (just an ID)

    @Column(nullable = false)
    private String userEmail;     // extracted from JWT - no need to call user-service

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;    // the bid amount - BigDecimal for money, never use double

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BidStatus status;

    private LocalDateTime placedAt;

    @PrePersist
    public void prePersist() {
        this.placedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = BidStatus.ACTIVE;
        }
    }
}