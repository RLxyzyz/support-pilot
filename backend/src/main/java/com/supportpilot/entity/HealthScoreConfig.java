package com.supportpilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** M7 健康分权重配置（按租户唯一）。 */
@Data
@Entity
@Table(name = "health_score_config")
public class HealthScoreConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "freq_weight")
    private Integer freqWeight;

    @Column(name = "sla_weight")
    private Integer slaWeight;

    @Column(name = "csat_weight")
    private Integer csatWeight;

    @Column(name = "sentiment_weight")
    private Integer sentimentWeight;
}
