package com.supportpilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

/** M6 会话摘要：保留 AI 原始版与人工修正版。 */
@Data
@Entity
@Table(name = "ticket_summary")
public class TicketSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "original_content", columnDefinition = "text")
    private String originalContent;

    @Column(name = "edited_content", columnDefinition = "text")
    private String editedContent;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
