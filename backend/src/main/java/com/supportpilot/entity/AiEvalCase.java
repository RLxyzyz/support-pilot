package com.supportpilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

/** M3 AI 评测用例：从草稿采纳 / 驳回沉淀而来。 */
@Data
@Entity
@Table(name = "ai_eval_case")
public class AiEvalCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id")
    private Long ticketId;

    private String question;

    @Column(name = "expected_points", columnDefinition = "text")
    private String expectedPoints;

    @Column(name = "accept_criteria", columnDefinition = "text")
    private String acceptCriteria;

    @Column(name = "reject_reason")
    private String rejectReason;

    private String status;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "created_at")
    private Instant createdAt;
}
