package com.supportpilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

/** M4 L0 自动解决配置：按分类白名单与置信度阈值。 */
@Data
@Entity
@Table(name = "auto_resolve_config")
public class AutoResolveConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String category;

    private Boolean enabled;

    private Double threshold;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
