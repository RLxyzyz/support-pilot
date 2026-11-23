package com.supportpilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "agent_step")
public class AgentStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id")
    private Long runId;

    private String tag;

    private String cls;

    @Column(name = "time")
    private String time;

    @Column(columnDefinition = "text")
    private String html;

    @Column(name = "created_at")
    private Instant createdAt;
}
