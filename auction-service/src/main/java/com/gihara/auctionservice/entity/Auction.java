package com.gihara.auctionservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity                      // tells JPA "this is a DB table"
@Table(name = "auctions")    // the actual table name in Postgres
@Getter @Setter              // Lombok generates getters/setters
@NoArgsConstructor           // Lombok generates empty constructor
@AllArgsConstructor          // Lombok generates full constructor
@Builder                     // Lombok: lets you do Auction.builder().title("x").build()
public class Auction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // auto-increment PK
    private Long id;

    @Column(nullable = false)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)   // stores enum as "OPENED", "IN_PROG" etc in DB
    @Column(nullable = false)
    private AuctionStatus status;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private Long createdByUserId;   // reference to user-service (just an ID, no join)

    private LocalDateTime createdAt;

    @PrePersist   // runs automatically before INSERT
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = AuctionStatus.OPENED;
        }
    }
}