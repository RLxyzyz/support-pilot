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
 * 工单附件（V2.1 M4）：可挂在工单主体或某条消息上。
 */
@Data
@Entity
@Table(name = "attachment")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    /** 关联的消息 id；为空表示挂在工单主体上。 */
    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "uploader_id")
    private Long uploaderId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(length = 16)
    private String ext;

    @Column(length = 128)
    private String mime;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    /** CUSTOMER / INTERNAL。 */
    @Column(nullable = false, length = 16)
    private String visibility = "CUSTOMER";

    @Column(nullable = false)
    private Boolean deleted = false;

    @Column(nullable = false)
    private Boolean purged = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
