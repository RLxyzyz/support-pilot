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
 * 快捷回复模板（#5）。按租户隔离；content 支持 {客户名} 等变量，回复时由前端替换。
 */
@Data
@Entity
@Table(name = "reply_template")
public class ReplyTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    private String name;

    @Column(columnDefinition = "text")
    private String content;

    private String category;

    @Column(name = "created_at")
    private Instant createdAt;
}
