package com.supportpilot.dto;

import lombok.Data;

import java.time.Instant;

/**
 * 操作日志 VO。列表接口仅返回基础字段（before/after/params 为 null），
 * 详情接口返回全部字段含快照。
 */
@Data
public class OperationLogVO {
    private Long id;
    private Long tenantId;
    private Long operatorId;
    private String operatorName;
    private String operatorUsername;
    private String module;
    private String action;
    private String targetType;
    private String targetId;
    private String summary;
    private String result;
    private String errorMsg;
    private String ip;
    private String userAgent;
    private Integer costMs;
    private Instant createdAt;
    // 以下大字段仅详情接口返回
    private String beforeData;
    private String afterData;
    private String requestParams;
}
