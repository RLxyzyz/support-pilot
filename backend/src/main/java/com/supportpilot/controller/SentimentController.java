package com.supportpilot.controller;

import com.supportpilot.common.Result;
import com.supportpilot.common.Role;
import com.supportpilot.common.audit.OperationAudit;
import com.supportpilot.common.security.RequireRole;
import com.supportpilot.service.SentimentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** M5 情绪预警：优先级升级与客户情绪趋势。 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SentimentController {

    private final SentimentService sentimentService;

    @RequireRole({Role.ADMIN, Role.MANAGER, Role.ENGINEER})
    @OperationAudit(module = "ticket", action = "sentiment-upgrade", targetType = "ticket", summary = "负面情绪升级优先级")
    @PostMapping("/tickets/{id}/sentiment/upgrade")
    public Result<Map<String, Object>> upgrade(@PathVariable Long id) {
        return Result.ok(sentimentService.upgrade(id));
    }

    @RequireRole({Role.ADMIN, Role.MANAGER, Role.ENGINEER})
    @GetMapping("/customers/{id}/sentiment/trend")
    public Result<List<Map<String, Object>>> trend(@PathVariable Long id) {
        return Result.ok(sentimentService.trend(id));
    }
}
