package com.supportpilot.controller;

import com.supportpilot.common.Result;
import com.supportpilot.common.Role;
import com.supportpilot.common.audit.OperationAudit;
import com.supportpilot.common.security.RequireRole;
import com.supportpilot.service.StormService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** M2 故障风暴：活跃风暴、升级为事件单、恢复与忽略。 */
@RestController
@RequestMapping("/api/storms")
@RequiredArgsConstructor
public class StormController {

    private final StormService stormService;

    @RequireRole({Role.ADMIN, Role.MANAGER, Role.ENGINEER})
    @GetMapping("/active")
    public Result<List<Map<String, Object>>> active() {
        return Result.ok(stormService.active());
    }

    @RequireRole({Role.ADMIN, Role.MANAGER})
    @OperationAudit(module = "storm", action = "upgrade", targetType = "storm", summary = "风暴升级为事件单")
    @PostMapping("/{id}/upgrade")
    public Result<Map<String, Object>> upgrade(@PathVariable Long id) {
        return Result.ok(stormService.upgrade(id));
    }

    @RequireRole({Role.ADMIN, Role.MANAGER})
    @OperationAudit(module = "storm", action = "recover", targetType = "storm", summary = "风暴恢复")
    @PostMapping("/{id}/recover")
    public Result<Map<String, Object>> recover(@PathVariable Long id) {
        return Result.ok(stormService.recover(id));
    }

    @RequireRole({Role.ADMIN, Role.MANAGER})
    @OperationAudit(module = "storm", action = "dismiss", targetType = "storm", summary = "忽略风暴")
    @PostMapping("/{id}/dismiss")
    public Result<Map<String, Object>> dismiss(@PathVariable Long id) {
        return Result.ok(stormService.dismiss(id));
    }
}
