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
 * SLA 策略矩阵（V2.1 FR-S01）：服务等级 × 优先级 → 首响 / 解决时限与计时日历。
 */
@Data
@Entity
@Table(name = "sla_policy")
public class SlaPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 租户标识；0 表示演示租户（与 ticket.tenant_id 的可空语义解耦）。 */
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** 金牌 / 银牌 / 铜牌 / 默认。 */
    @Column(nullable = false, length = 16)
    private String tier;

    @Column(nullable = false, length = 8)
    private String priority;

    @Column(name = "first_response_minutes", nullable = false)
    private Integer firstResponseMinutes;

    @Column(name = "resolve_minutes", nullable = false)
    private Integer resolveMinutes;

    /** NATURAL / WORKTIME。 */
    @Column(name = "calendar_type", nullable = false, length = 16)
    private String calendarType = "NATURAL";

    @Column(name = "calendar_id")
    private Long calendarId;

    /** 预警阈值百分比（50–95）。 */
    @Column(name = "warning_threshold", nullable = false)
    private Integer warningThreshold = 80;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
