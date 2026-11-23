package com.supportpilot.controller;

import com.supportpilot.common.Result;
import com.supportpilot.common.Role;
import com.supportpilot.common.audit.OperationAudit;
import com.supportpilot.common.security.RequireRole;
import com.supportpilot.service.SummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** M6 会话摘要：自动生成（消息 ≥10 条）与人工修正。 */
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class SummaryController {

    private final SummaryService summaryService;

    public record SummaryRequest(String content) {
    }

    @RequireRole({Role.ADMIN, Role.MANAGER, Role.ENGINEER})
    @GetMapping("/{id}/summary")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        return Result.ok(summaryService.get(id));
    }

    @RequireRole({Role.ADMIN, Role.MANAGER, Role.ENGINEER})
    @OperationAudit(module = "ticket", action = "summary-update", targetType = "ticket", summary = "修改会话摘要")
    @PutMapping("/{id}/summary")
    public Result<Map<String, Object>> update(@PathVariable Long id,
                                              @RequestBody SummaryRequest request) {
        return Result.ok(summaryService.update(id, request.content()));
    }
}
