package com.supportpilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

/** M5 情绪分析结果：按消息记录情绪与强度。 */
@Data
@Entity
@Table(name = "sentiment_result")
public class SentimentResult {

    public static final String POSITIVE = "POSITIVE";
    public static final String NEUTRAL = "NEUTRAL";
    public static final String NEGATIVE = "NEGATIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "message_id")
    private Long messageId;

    private String sentiment;

    private Double intensity;

    private String keywords;

    @Column(name = "created_at")
    private Instant createdAt;
}
