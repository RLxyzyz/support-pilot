package com.supportpilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;

/**
 * 日历节假日（V2.1 FR-S06）：节假日不计入工作时长。
 */
@Data
@Entity
@Table(name = "sla_holiday")
public class SlaHoliday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "calendar_id", nullable = false)
    private Long calendarId;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(length = 64)
    private String name;
}
