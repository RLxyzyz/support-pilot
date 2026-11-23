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
 * 操作审计日志：记录谁在何时对什么做了什么，含操作前/后快照、请求参数、结果、IP 等。
 * 由 OperationAuditAspect 采集写入；登录/注册由 UserService 手动记录。
 */
@Data
@Entity
@Table(name = "operation_log")
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_name")
    private String operatorName;

    @Column(name = "operator_username")
    private String operatorUsername;

    /** 模块：auth / ticket / kb / user / draft / agent */
    private String module;

    /** 动作：login / register / create / update / delete / assign / accept / escalate / approve / reject ... */
    private String action;

    /** 操作对象类型：ticket / kb_doc / user / draft */
    @Column(name = "target_type")
    private String targetType;

    @Column(name = "target_id")
    private String targetId;

    /** 人类可读摘要 */
    private String summary;

    /** 操作前快照 JSON */
    @Column(name = "before_data", columnDefinition = "text")
    private String beforeData;

    /** 操作后快照 JSON */
    @Column(name = "after_data", columnDefinition = "text")
    private String afterData;

    /** 请求参数 JSON（password 等字段已脱敏） */
    @Column(name = "request_params", columnDefinition = "text")
    private String requestParams;

    /** success / fail */
    private String result;

    @Column(name = "error_msg", columnDefinition = "text")
    private String errorMsg;

    private String ip;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "cost_ms")
    private Integer costMs;

    @Column(name = "created_at")
    private Instant createdAt;
}
