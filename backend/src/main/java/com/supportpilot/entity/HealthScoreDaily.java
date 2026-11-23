package com.supportpilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/** M7 客户健康分日快照。 */
@Data
@Entity
@Table(name = "health_score_daily")
public class HealthScoreDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "score_date")
    private LocalDate scoreDate;

    private Double score;

    @Column(name = "freq_score")
    private Double freqScore;

    @Column(name = "sla_score")
    private Double slaScore;

    @Column(name = "csat_score")
    private Double csatScore;

    @Column(name = "sentiment_score")
    private Double sentimentScore;

    @Column(name = "created_at")
    private Instant createdAt;
}
