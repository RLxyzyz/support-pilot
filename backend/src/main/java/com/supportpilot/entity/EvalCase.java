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
 * 评测样本 / 知识盲区（看板 misses 来源）。
 */
@Data
@Entity
@Table(name = "eval_case")
public class EvalCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String question;

    private Integer count;

    private Integer weight;

    /** 租户隔离：RAG 未命中写入时设置；种子演示数据为 null（全局）。 */
    @Column(name = "tenant_id")
    private Long tenantId;

    /** 来源：rag_miss（RAG 未命中）/ seed（演示种子）等。 */
    private String source;

    /** 最近一次被问及/命中的时间，用于热度排序。 */
    @Column(name = "last_asked_at")
    private Instant lastAskedAt;

    @Column(name = "created_at")
    private Instant createdAt;
}
