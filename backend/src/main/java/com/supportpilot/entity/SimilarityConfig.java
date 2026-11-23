package com.supportpilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** M1 相似检测配置（按租户唯一）。 */
@Data
@Entity
@Table(name = "similarity_config")
public class SimilarityConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    private Double threshold;

    private Boolean enabled;

    @Column(name = "window_days")
    private Integer windowDays;
}
