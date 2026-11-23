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
 * 工单关联 / 合并（#4）。一条记录表示 ticketId 与 relatedTicketId 的关联，
 * 查询时任一端均可反向看到对方（双向可见），避免重复存两行。
 */
@Data
@Entity
@Table(name = "ticket_relation")
public class TicketRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "related_ticket_id")
    private Long relatedTicketId;

    /** 关联类型：related（相关）/ duplicate（重复）/ parent（父子）。 */
    @Column(name = "relation_type")
    private String relationType;

    @Column(name = "created_at")
    private Instant createdAt;
}
