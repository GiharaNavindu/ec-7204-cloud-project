package com.gihara.notificationservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gihara.notificationservice.dto.NotificationPageResponse;
import com.gihara.notificationservice.dto.NotificationReadAllResponse;
import com.gihara.notificationservice.dto.NotificationResponse;
import com.gihara.notificationservice.dto.UnreadCountResponse;
import com.gihara.notificationservice.entity.NotificationChannel;
import com.gihara.notificationservice.entity.NotificationStatus;
import com.gihara.notificationservice.entity.NotificationType;
import com.gihara.notificationservice.exception.GlobalExceptionHandler;
import com.gihara.notificationservice.service.NotificationService;
import com.gihara.notificationservice.util.JwtProvider;

class NotificationControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private NotificationService notificationService;

    @Mock
    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        NotificationController controller = new NotificationController(notificationService, jwtProvider);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createNotification_shouldReturnCreatedWhenUserMatchesToken() throws Exception {
        NotificationResponse response = NotificationResponse.builder()
                .id(21L)
                .userId(7L)
                .type(NotificationType.AUCTION_WON)
                .title("Auction won")
                .message("You won it")
                .status(NotificationStatus.UNREAD)
                .channel(NotificationChannel.IN_APP)
                .createdAt(LocalDateTime.of(2026, 4, 28, 10, 0))
                .build();

        when(jwtProvider.getUserIdFromToken("valid-token")).thenReturn(7L);
        when(jwtProvider.getRoleFromToken("valid-token")).thenReturn("TRUSTED_USER");
        when(notificationService.createNotification(any())).thenReturn(response);

        String requestBody = """
                {
                  "userId": 7,
                  "type": "AUCTION_WON",
                  "title": "Auction won",
                  "message": "You won it"
                }
                """;

        mockMvc.perform(post("/api/notifications")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(21))
                .andExpect(jsonPath("$.userId").value(7));
    }

    @Test
    void getUserNotifications_shouldRejectOtherUsers() throws Exception {
        when(jwtProvider.getUserIdFromToken("valid-token")).thenReturn(3L);
        when(jwtProvider.getRoleFromToken("valid-token")).thenReturn("TRUSTED_USER");

        mockMvc.perform(get("/api/notifications/user/4")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You are not allowed to access notifications for this user"));
    }

    @Test
    void getUserNotifications_shouldReturnPageForAdmin() throws Exception {
        NotificationPageResponse response = NotificationPageResponse.builder()
                .content(List.of(NotificationResponse.builder()
                        .id(1L)
                        .userId(9L)
                        .type(NotificationType.BID_OUTBID)
                        .title("Outbid")
                        .message("You were outbid")
                        .status(NotificationStatus.UNREAD)
                        .channel(NotificationChannel.IN_APP)
                        .createdAt(LocalDateTime.of(2026, 4, 28, 10, 0))
                        .build()))
                .pageNumber(0)
                .pageSize(20)
                .totalElements(1)
                .totalPages(1)
                .last(true)
                .build();

        when(jwtProvider.getUserIdFromToken("admin-token")).thenReturn(1L);
        when(jwtProvider.getRoleFromToken("admin-token")).thenReturn("ADMIN");
        when(notificationService.getUserNotifications(9L, 0, 20)).thenReturn(response);

        mockMvc.perform(get("/api/notifications/user/9")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void markAsRead_shouldReturnUpdatedNotification() throws Exception {
        NotificationResponse response = NotificationResponse.builder()
                .id(5L)
                .userId(7L)
                .status(NotificationStatus.READ)
                .channel(NotificationChannel.IN_APP)
                .title("Auction won")
                .message("You won it")
                .type(NotificationType.AUCTION_WON)
                .createdAt(LocalDateTime.of(2026, 4, 28, 9, 0))
                .readAt(LocalDateTime.of(2026, 4, 28, 9, 5))
                .build();

        when(jwtProvider.getUserIdFromToken("valid-token")).thenReturn(7L);
        when(notificationService.markAsRead(5L, 7L)).thenReturn(response);

        mockMvc.perform(patch("/api/notifications/5/read")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READ"));
    }

    @Test
    void markAllAsRead_shouldReturnUpdatedCount() throws Exception {
        when(jwtProvider.getUserIdFromToken("valid-token")).thenReturn(7L);
        when(jwtProvider.getRoleFromToken("valid-token")).thenReturn("TRUSTED_USER");
        when(notificationService.markAllAsRead(7L)).thenReturn(NotificationReadAllResponse.builder()
                .userId(7L)
                .updatedCount(4)
                .build());

        mockMvc.perform(patch("/api/notifications/user/7/read-all")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(4));
    }

    @Test
    void getUnreadCount_shouldReturnCount() throws Exception {
        when(jwtProvider.getUserIdFromToken("valid-token")).thenReturn(7L);
        when(jwtProvider.getRoleFromToken("valid-token")).thenReturn("TRUSTED_USER");
        when(notificationService.getUnreadCount(7L)).thenReturn(UnreadCountResponse.builder()
                .userId(7L)
                .unreadCount(2)
                .build());

        mockMvc.perform(get("/api/notifications/user/7/unread-count")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2));
    }
}
