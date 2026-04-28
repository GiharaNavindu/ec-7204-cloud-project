package com.gihara.notificationservice.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gihara.notificationservice.entity.Notification;
import com.gihara.notificationservice.entity.NotificationStatus;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserId(Long userId, Pageable pageable);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    boolean existsByReferenceId(String referenceId);

    long countByUserIdAndStatus(Long userId, NotificationStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
            set n.status = com.gihara.notificationservice.entity.NotificationStatus.READ,
                n.readAt = :readAt
            where n.userId = :userId and n.status = com.gihara.notificationservice.entity.NotificationStatus.UNREAD
            """)
    int markAllAsReadByUserId(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);
}
