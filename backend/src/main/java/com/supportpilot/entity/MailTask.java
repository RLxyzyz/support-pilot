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
 * 邮件发送任务（V2.0 EP3）：异步发送 + 重试状态机。
 * status: pending / sent / failed；retry_count 记录已重试次数；last_error 记录最近失败原因。
 */
@Data
@Entity
@Table(name = "mail_task")
public class MailTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "notification_id")
    private Long notificationId;

    @Column(name = "to_addr", nullable = false, length = 256)
    private String toAddr;

    @Column(nullable = false, length = 256)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 16)
    private String status = "pending";

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "last_error", length = 512)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
