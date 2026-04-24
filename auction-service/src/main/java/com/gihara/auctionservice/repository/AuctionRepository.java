package com.gihara.auctionservice.repository;

import com.gihara.auctionservice.entity.Auction;
import com.gihara.auctionservice.entity.AuctionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// JpaRepository<EntityType, PrimaryKeyType>
// gives you: save(), findById(), findAll(), delete() etc for free
public interface AuctionRepository extends JpaRepository<Auction, Long> {

    // Spring reads the method name and generates the SQL automatically
    List<Auction> findByStatus(AuctionStatus status);

    List<Auction> findByCreatedByUserId(Long userId);
}