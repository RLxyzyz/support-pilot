package com.supportpilot.controller;

import com.supportpilot.common.Result;
import com.supportpilot.common.Role;
import com.supportpilot.common.audit.OperationAudit;
import com.supportpilot.common.security.RequireRole;
import com.supportpilot.service.L0Service;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** M4 L0 自助闭环：配置、客户确认、重开、转人工与效果报表。 */
@RestController
@RequestMapping("/api/l0")
@RequiredArgsConstructor
public class L0Controller {

    private final L0Service l0Service;

    @RequireRole({Role.ADMIN, Role.MANAGER, Role.ENGINEER})
    @GetMapping("/config")
    public Result<Map<String, Object>> config() {
        return Result.ok(l0Service.getConfig());
    }

    @RequireRole({Role.ADMIN})
    @OperationAudit(module = "l0", action = "update", targetType = "config", summary = "更新 L0 自助应答配置")
    @PutMapping("/config")
    public Result<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> body) {
        Boolean confirm = body.get("confirm") instanceof Boolean b ? b : null;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = body.get("categories") instanceof List<?> list
                ? (List<Map<String, Object>>) list : List.of();
        return Result.ok(l0Service.updateConfig(confirm, items));
    }

    @PostMapping("/confirm")
    public Result<Map<String, Object>> confirm(@RequestParam String token) {
        return Result.ok(l0Service.confirm(token));
    }

    @RequireRole({Role.ADMIN, Role.MANAGER, Role.ENGINEER})
    @OperationAudit(module = "l0", action = "reopen", targetType = "ticket", summary = "L0 工单重开")
    @PostMapping("/reopen/{ticketId}")
    public Result<Map<String, Object>> reopen(@PathVariable Long ticketId) {
        return Result.ok(l0Service.reopen(ticketId));
    }

    @RequireRole({Role.ADMIN, Role.MANAGER, Role.ENGINEER})
    @OperationAudit(module = "l0", action = "transfer", targetType = "ticket", summary = "L0 工单转人工")
    @PostMapping("/transfer/{ticketId}")
    public Result<Map<String, Object>> transfer(@PathVariable Long ticketId) {
        return Result.ok(l0Service.transfer(ticketId));
    }

    @RequireRole({Role.ADMIN, Role.MANAGER, Role.ENGINEER})
    @GetMapping("/report")
    public Result<List<Map<String, Object>>> report() {
        return Result.ok(l0Service.report());
    }
}
