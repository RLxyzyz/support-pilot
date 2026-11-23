package com.supportpilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "product")
public class Product {

    @Id
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    private String name;

    @Column(name = "created_at")
    private Instant createdAt;
}
