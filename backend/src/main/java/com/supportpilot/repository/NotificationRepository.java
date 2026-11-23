package com.supportpilot.repository;

import com.supportpilot.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, String type, Pageable pageable);

    long countByUserIdAndIsReadFalse(Long userId);

    @Modifying
    @Query("update Notification n set n.isRead = true, n.readAt = :now where n.userId = :userId and n.isRead = false")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);

    /** 清理 N 天前的历史通知（保留策略，默认 90 天）。 */
    long deleteByCreatedAtBefore(Instant cutoff);
}
