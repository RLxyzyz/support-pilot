package com.supportpilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

/**
 * 站内信通知（V2.0 EP3）。用户维度存储，支持未读数、标记已读与 90 天清理。
 */
@Data
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 事件类型，见 {@link com.supportpilot.common.NotifyType}。 */
    @Column(nullable = false, length = 32)
    private String type;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(length = 512)
    private String content;

    @Column(name = "ref_type", length = 32)
    private String refType;

    @Column(name = "ref_id", length = 64)
    private String refId;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
