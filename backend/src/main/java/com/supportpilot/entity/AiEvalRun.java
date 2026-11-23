package com.supportpilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

/** M3 评测运行记录：保留通过率与较上次变化。 */
@Data
@Entity
@Table(name = "ai_eval_run")
public class AiEvalRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_label")
    private String versionLabel;

    private Integer total;

    private Integer passed;

    @Column(name = "pass_rate")
    private Double passRate;

    @Column(name = "prev_pass_rate")
    private Double prevPassRate;

    @Column(name = "detail_json", columnDefinition = "longtext")
    private String detailJson;

    @Column(name = "created_at")
    private Instant createdAt;
}
