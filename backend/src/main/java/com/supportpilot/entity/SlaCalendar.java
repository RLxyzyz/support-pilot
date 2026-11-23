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
 * 计时日历（V2.1 FR-S06）：工作日位图 + 最多 3 段工作时段 + 时区。
 */
@Data
@Entity
@Table(name = "sla_calendar")
public class SlaCalendar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 64)
    private String name;

    /** 逗号分隔的星期位图，1=周一 … 7=周日。 */
    @Column(nullable = false, length = 32)
    private String workdays = "1,2,3,4,5";

    /** 逗号分隔的工作时段，如 09:00-12:00,13:30-18:00。 */
    @Column(nullable = false, length = 128)
    private String segments = "09:00-18:00";

    @Column(nullable = false, length = 64)
    private String timezone = "Asia/Shanghai";

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
