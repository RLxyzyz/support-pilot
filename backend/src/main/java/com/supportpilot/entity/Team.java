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
 * 工作组（V2.0 EP2 前置）：支撑数据权限「本组」范围。队列 / 认领 / 值班表留 V2.1。
 */
@Data
@Entity
@Table(name = "team")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    private String name;

    /** active / disabled。 */
    private String status = "active";

    @Column(name = "created_at")
    private Instant createdAt;
}
