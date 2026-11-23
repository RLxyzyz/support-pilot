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
 * 知识分片（embedding 向量列由真实 RAG 流程写入，实体暂不映射该列）。
 */
@Data
@Entity
@Table(name = "kb_chunk")
public class KbChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doc_id")
    private Long docId;

    @Column(columnDefinition = "text")
    private String content;

    @Column(name = "created_at")
    private Instant createdAt;
}
