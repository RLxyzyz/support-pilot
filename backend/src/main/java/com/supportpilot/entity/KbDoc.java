package com.supportpilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "kb_doc")
public class KbDoc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    private String name;

    private String path;

    @Column(name = "product_line")
    private String productLine;

    private Integer chunks;

    private Integer hits;

    private String status;

    @Column(name = "created_at")
    private Instant createdAt;
}
