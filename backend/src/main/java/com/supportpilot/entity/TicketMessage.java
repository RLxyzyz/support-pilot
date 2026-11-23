package com.supportpilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "ticket_message")
public class TicketMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    private String who;

    private String role;

    @Column(columnDefinition = "text")
    private String text;

    private Boolean internal;

    /** 来源侧评论唯一 id（如 GitHub comment id），用于评论增量同步去重；null 表示非外部同步来源。 */
    @Column(name = "external_id")
    private Long externalId;

    /** M5 情绪分析：POSITIVE / NEUTRAL / NEGATIVE，null 表示未分析。 */
    private String sentiment;

    /** M5 情绪强度 0-1。 */
    @Column(name = "sentiment_intensity")
    private Double sentimentIntensity;

    @Column(name = "created_at")
    private Instant createdAt;

    /** created_at 列 NOT NULL；各写入方未显式赋值时兜底为当前时间。 */
    @PrePersist
    void fillCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
