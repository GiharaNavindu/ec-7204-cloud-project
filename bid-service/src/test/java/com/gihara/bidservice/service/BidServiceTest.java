package com.gihara.bidservice.service;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.gihara.bidservice.dto.AuctionResponse;
import com.gihara.bidservice.dto.BidRequest;
import com.gihara.bidservice.dto.BidResponse;
import com.gihara.bidservice.entity.Bid;
import com.gihara.bidservice.entity.BidStatus;
import com.gihara.bidservice.event.BidPlacedEvent;
import com.gihara.bidservice.repository.BidRepository;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    @Mock
    private BidRepository bidRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private BidService bidService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(bidService, "exchange", "bid.exchange");
        ReflectionTestUtils.setField(bidService, "routingKey", "bid.placed");
        ReflectionTestUtils.setField(bidService, "auctionServiceUrl", "http://auction-service:8082");
    }

    @Test
    void placeBid_shouldSaveBidAndPublishEvent() {
        BidRequest request = new BidRequest();
        request.setAuctionId(8L);
        request.setAmount(new BigDecimal("125.50"));

        AuctionResponse auction = new AuctionResponse();
        auction.setId(8L);
        auction.setStatus("IN_PROG");

        when(restTemplate.getForObject(eq("http://auction-service:8082/api/auctions/8"), eq(AuctionResponse.class)))
                .thenReturn(auction);
        when(bidRepository.findTopByAuctionIdOrderByAmountDesc(8L)).thenReturn(Optional.empty());
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> {
            Bid bid = invocation.getArgument(0);
            bid.setId(22L);
            return bid;
        });

        BidResponse response = bidService.placeBid(request, "jane@example.com", 4L);

        ArgumentCaptor<Bid> bidCaptor = ArgumentCaptor.forClass(Bid.class);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(bidRepository).save(bidCaptor.capture());
        verify(rabbitTemplate).convertAndSend(eq("bid.exchange"), eq("bid.placed"), eventCaptor.capture());

        Bid savedBid = bidCaptor.getValue();
        BidPlacedEvent publishedEvent = (BidPlacedEvent) eventCaptor.getValue();
        assertEquals(8L, savedBid.getAuctionId());
        assertEquals(4L, savedBid.getUserId());
        assertEquals("jane@example.com", savedBid.getUserEmail());
        assertEquals(BidStatus.ACTIVE, savedBid.getStatus());
        assertEquals(22L, response.getId());
        assertEquals(new BigDecimal("125.50"), response.getAmount());
        assertNotNull(publishedEvent.getEventId());
        assertEquals(22L, publishedEvent.getBidId());
        assertEquals(8L, publishedEvent.getAuctionId());
        assertNull(publishedEvent.getPreviousHighestBidderUserId());
    }

    @Test
    void placeBid_shouldPublishOutbidDetailsWhenReplacingAnotherUser() {
        BidRequest request = new BidRequest();
        request.setAuctionId(8L);
        request.setAmount(new BigDecimal("150.00"));

        AuctionResponse auction = new AuctionResponse();
        auction.setId(8L);
        auction.setStatus("IN_PROG");

        Bid currentHighest = Bid.builder()
                .id(7L)
                .auctionId(8L)
                .userId(9L)
                .userEmail("previous@example.com")
                .amount(new BigDecimal("120.00"))
                .status(BidStatus.ACTIVE)
                .build();

        when(restTemplate.getForObject(eq("http://auction-service:8082/api/auctions/8"), eq(AuctionResponse.class)))
                .thenReturn(auction);
        when(bidRepository.findTopByAuctionIdOrderByAmountDesc(8L)).thenReturn(Optional.of(currentHighest));
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> {
            Bid bid = invocation.getArgument(0);
            bid.setId(22L);
            return bid;
        });

        bidService.placeBid(request, "jane@example.com", 4L);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq("bid.exchange"), eq("bid.placed"), eventCaptor.capture());

        BidPlacedEvent publishedEvent = (BidPlacedEvent) eventCaptor.getValue();
        assertEquals(7L, publishedEvent.getPreviousHighestBidId());
        assertEquals(9L, publishedEvent.getPreviousHighestBidderUserId());
        assertEquals("previous@example.com", publishedEvent.getPreviousHighestBidderEmail());
        assertEquals(new BigDecimal("120.00"), publishedEvent.getPreviousHighestAmount());
    }

    @Test
    void placeBid_shouldRejectLowerBidThanCurrentHighest() {
        BidRequest request = new BidRequest();
        request.setAuctionId(8L);
        request.setAmount(new BigDecimal("80.00"));

        AuctionResponse auction = new AuctionResponse();
        auction.setId(8L);
        auction.setStatus("IN_PROG");

        Bid currentHighest = Bid.builder()
                .auctionId(8L)
                .amount(new BigDecimal("100.00"))
                .build();

        when(restTemplate.getForObject(eq("http://auction-service:8082/api/auctions/8"), eq(AuctionResponse.class)))
                .thenReturn(auction);
        when(bidRepository.findTopByAuctionIdOrderByAmountDesc(8L)).thenReturn(Optional.of(currentHighest));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> bidService.placeBid(request, "jane@example.com", 4L));

        assertEquals("Bid amount must be higher than current highest bid of 100.00", ex.getMessage());
        verify(bidRepository, never()).save(any());
        verifyNoInteractions(rabbitTemplate);
    }
}
