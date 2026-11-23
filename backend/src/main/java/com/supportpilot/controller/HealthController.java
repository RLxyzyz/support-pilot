package com.supportpilot.controller;

import com.supportpilot.common.Result;
import com.supportpilot.common.Role;
import com.supportpilot.common.audit.OperationAudit;
import com.supportpilot.common.security.RequireRole;
import com.supportpilot.entity.HealthScoreConfig;
import com.supportpilot.service.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** M7 客户健康分：当前分数与趋势、权重配置。 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;

    public record HealthConfigRequest(Integer freqWeight, Integer slaWeight,
                                      Integer csatWeight, Integer sentimentWeight) {
    }

    @RequireRole({Role.ADMIN, Role.MANAGER, Role.ENGINEER})
    @GetMapping("/customers/{id}/health")
    public Result<Map<String, Object>> health(@PathVariable Long id) {
        return Result.ok(healthService.health(id));
    }

    @RequireRole({Role.ADMIN, Role.MANAGER, Role.ENGINEER})
    @GetMapping("/health/config")
    public Result<HealthScoreConfig> config() {
        return Result.ok(healthService.getConfig());
    }

    @RequireRole({Role.ADMIN})
    @OperationAudit(module = "health", action = "update", targetType = "config", summary = "更新健康分权重")
    @PutMapping("/health/config")
    public Result<HealthScoreConfig> updateConfig(@RequestBody HealthConfigRequest request) {
        return Result.ok(healthService.updateConfig(request.freqWeight(), request.slaWeight(),
                request.csatWeight(), request.sentimentWeight()));
    }
}
