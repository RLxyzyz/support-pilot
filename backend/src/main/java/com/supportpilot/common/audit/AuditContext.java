package com.supportpilot.common.audit;

/**
 * 审计上下文：切面 / 登录记录构造后传给 OperationLogService 落库。
 * 操作人字段由后端认证上下文（UserContext）填充，不接受前端传入，防伪造。
 */
public record AuditContext(
        Long operatorId,
        String operatorName,
        String operatorUsername,
        Long tenantId,
        String module,
        String action,
        String targetType,
        String targetId,
        String summary
) {
}
