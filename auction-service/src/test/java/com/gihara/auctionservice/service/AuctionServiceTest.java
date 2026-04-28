package com.gihara.auctionservice.service;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.gihara.auctionservice.dto.AuctionRequest;
import com.gihara.auctionservice.dto.AuctionResponse;
import com.gihara.auctionservice.entity.Auction;
import com.gihara.auctionservice.entity.AuctionStatus;
import com.gihara.auctionservice.repository.AuctionRepository;

@ExtendWith(MockitoExtension.class)
class AuctionServiceTest {

    @Mock
    private AuctionRepository auctionRepository;

    @InjectMocks
    private AuctionService auctionService;

    @Test
    void createAuction_shouldPersistAuctionWithOpenedStatus() {
        AuctionRequest request = new AuctionRequest();
        request.setTitle("Vintage Clock");
        request.setDescription("Brass wall clock");
        request.setStartTime(LocalDateTime.of(2026, 4, 27, 10, 0));
        request.setEndTime(LocalDateTime.of(2026, 4, 27, 12, 0));
        request.setCreatedByUserId(7L);

        when(auctionRepository.save(any(Auction.class))).thenAnswer(invocation -> {
            Auction auction = invocation.getArgument(0);
            auction.setId(10L);
            auction.setCreatedAt(LocalDateTime.of(2026, 4, 27, 10, 15));
            return auction;
        });

        AuctionResponse response = auctionService.createAuction(request);

        ArgumentCaptor<Auction> captor = ArgumentCaptor.forClass(Auction.class);
        verify(auctionRepository).save(captor.capture());

        Auction savedAuction = captor.getValue();
        assertEquals("Vintage Clock", savedAuction.getTitle());
        assertEquals(AuctionStatus.OPENED, savedAuction.getStatus());
        assertEquals(7L, savedAuction.getCreatedByUserId());
        assertEquals(10L, response.getId());
        assertNotNull(response.getCreatedAt());
    }

    @Test
    void updateStatus_shouldPersistNewAuctionStatus() {
        Auction auction = Auction.builder()
                .id(12L)
                .title("Phone")
                .status(AuctionStatus.OPENED)
                .build();

        when(auctionRepository.findById(12L)).thenReturn(java.util.Optional.of(auction));
        when(auctionRepository.save(any(Auction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuctionResponse response = auctionService.updateStatus(12L, AuctionStatus.IN_PROG);

        assertEquals(AuctionStatus.IN_PROG, response.getStatus());
        assertEquals(12L, response.getId());
    }

    @Test
    void getAuctionById_shouldThrowWhenMissing() {
        when(auctionRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> auctionService.getAuctionById(99L));

        assertEquals("Auction not found: 99", ex.getMessage());
    }
}