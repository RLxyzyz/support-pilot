package com.supportpilot.controller;

import com.supportpilot.common.Result;
import com.supportpilot.common.Role;
import com.supportpilot.common.security.RequireRole;
import com.supportpilot.dto.OperationLogPageVO;
import com.supportpilot.dto.OperationLogVO;
import com.supportpilot.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 操作审计日志查询接口。鉴权：列表/详情均仅 admin（在 service.requireAdmin 校验）。
 * 数据按租户隔离（TenantContext）。
 */
@RestController
@RequestMapping("/api/operation-logs")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogService operationLogService;

    @RequireRole(Role.ADMIN)
    @GetMapping
    public Result<OperationLogPageVO> list(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(operationLogService.page(module, action, operator, start, end, page, size));
    }

    @RequireRole(Role.ADMIN)
    @GetMapping("/{id}")
    public Result<OperationLogVO> get(@PathVariable Long id) {
        return Result.ok(operationLogService.get(id));
    }
}
