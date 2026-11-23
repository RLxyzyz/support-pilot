package com.supportpilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

/** M2 故障风暴记录：同一分类在短窗口内工单激增时生成。 */
@Data
@Entity
@Table(name = "storm_record")
public class StormRecord {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_RECOVERED = "RECOVERED";
    public static final String STATUS_DISMISSED = "DISMISSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "storm_key")
    private String stormKey;

    private String category;

    @Column(name = "group_id")
    private String groupId;

    @Column(name = "`count`")
    private Integer count;

    @Column(name = "window_start")
    private Instant windowStart;

    @Column(name = "window_minutes")
    private Integer windowMinutes;

    private Integer threshold;

    private String status;

    @Column(name = "event_ticket_id")
    private Long eventTicketId;

    @Column(name = "last_ticket_id")
    private Long lastTicketId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
