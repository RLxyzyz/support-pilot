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
 * 客户联系人（V2.0 EP1）。同客户内 email 唯一；每客户仅一个主联系人（isPrimary=true）。
 */
@Data
@Entity
@Table(name = "contact")
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "customer_id")
    private Long customerId;

    private String name;

    private String email;

    private String phone;

    private String title;

    /** 是否主联系人。每客户仅一个 true；设新主联系人时旧的自动置 false。 */
    @Column(name = "is_primary")
    private Boolean isPrimary = false;

    @Column(name = "created_at")
    private Instant createdAt;
}
