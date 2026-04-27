package com.gihara.bidservice.repository;

import com.gihara.bidservice.entity.Bid;
import com.gihara.bidservice.entity.BidStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, Long> {

    List<Bid> findByAuctionId(Long auctionId);

    List<Bid> findByUserId(Long userId);

    // finds the current highest bid for an auction
    Optional<Bid> findTopByAuctionIdOrderByAmountDesc(Long auctionId);

    // finds all active bids for an auction
    List<Bid> findByAuctionIdAndStatus(Long auctionId, BidStatus status);
}