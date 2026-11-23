package com.supportpilot.controller;

import com.supportpilot.common.Result;
import com.supportpilot.common.Role;
import com.supportpilot.common.audit.OperationAudit;
import com.supportpilot.common.security.RequireRole;
import com.supportpilot.dto.NotifyRuleRequest;
import com.supportpilot.dto.NotifyRuleVO;
import com.supportpilot.service.NotifyRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 通知规则接口（V2.0 EP3），仅管理员可访问（类级 @RequireRole 由拦截器统一校验）。
 */
@RestController
@RequestMapping("/api/notify-rules")
@RequiredArgsConstructor
@RequireRole(Role.ADMIN)
public class NotifyRuleController {

    private final NotifyRuleService notifyRuleService;

    @GetMapping
    public Result<List<NotifyRuleVO>> list() {
        return Result.ok(notifyRuleService.list());
    }

    @OperationAudit(module = "notify", action = "create", summary = "新建通知规则")
    @PostMapping
    public Result<NotifyRuleVO> create(@Valid @RequestBody NotifyRuleRequest request) {
        return Result.ok(notifyRuleService.create(request));
    }

    @OperationAudit(module = "notify", action = "update", targetType = "notify_rule",
            targetId = "#id", summary = "修改通知规则 #{id}")
    @PutMapping("/{id}")
    public Result<NotifyRuleVO> update(@PathVariable Long id, @Valid @RequestBody NotifyRuleRequest request) {
        return Result.ok(notifyRuleService.update(id, request));
    }

    @OperationAudit(module = "notify", action = "delete", targetType = "notify_rule",
            targetId = "#id", summary = "删除通知规则 #{id}")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        notifyRuleService.delete(id);
        return Result.ok();
    }
}
