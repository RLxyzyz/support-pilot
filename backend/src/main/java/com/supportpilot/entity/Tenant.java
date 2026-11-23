package com.supportpilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "tenant")
public class Tenant {

    @Id
    private Long id;

    private String name;

    private String code;

    @Column(name = "created_at")
    private Instant createdAt;
}
