package com.supportpilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

/** M1 相似工单关系：ticket_id 与 similar_ticket_id 的相似度（单向存储）。 */
@Data
@Entity
@Table(name = "ticket_similarity")
public class TicketSimilarity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "similar_ticket_id")
    private Long similarTicketId;

    private Double score;

    private String channel;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "created_at")
    private Instant createdAt;
}
