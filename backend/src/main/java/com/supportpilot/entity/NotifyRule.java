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
 * 通知规则（V2.0 EP3）：事件类型 × 接收人 × 通道开关。
 *
 * <p>receivers 为逗号分隔的接收人标识（如 ASSIGNEE / MANAGER / USER:12 / ROLE:ADMIN），
 * 由 NotificationDispatcher 在事件触发时解析为具体用户。
 */
@Data
@Entity
@Table(name = "notify_rule")
public class NotifyRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(length = 64)
    private String name;

    @Column(name = "condition_json", length = 512)
    private String conditionJson;

    @Column(nullable = false, length = 256)
    private String receivers;

    @Column(name = "in_app_enabled", nullable = false)
    private Boolean inAppEnabled = true;

    @Column(name = "email_enabled", nullable = false)
    private Boolean emailEnabled = false;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
