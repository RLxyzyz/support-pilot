package com.supportpilot.controller;

import com.supportpilot.common.Result;
import com.supportpilot.common.Role;
import com.supportpilot.common.audit.OperationAudit;
import com.supportpilot.common.security.RequireRole;
import com.supportpilot.service.GitHubSyncConfigService;
import com.supportpilot.service.GitHubSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * GitHub Issue 同步配置与手动触发。
 *
 * <p>页面可配置目标仓库、指定 Issue、单次同步条数、每条 Issue 的评论条数（0=全部）与 Token。
 */
@RestController
@RequestMapping("/api/github-sync")
@RequiredArgsConstructor
public class GitHubSyncController {

    private final GitHubSyncConfigService configService;
    private final GitHubSyncService syncService;

    @GetMapping("/config")
    @RequireRole({Role.ADMIN, Role.MANAGER})
    public Result<Map<String, Object>> getConfig() {
        return Result.ok(configService.get());
    }

    @PutMapping("/config")
    @RequireRole({Role.ADMIN, Role.MANAGER})
    @OperationAudit(module = "系统设置", action = "保存 GitHub 同步配置", targetType = "github_sync_config")
    public Result<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> req) {
        return Result.ok(configService.update(req));
    }

    @PostMapping("/run")
    @RequireRole({Role.ADMIN, Role.MANAGER})
    @OperationAudit(module = "系统设置", action = "手动同步 GitHub Issue", targetType = "github_sync_config")
    public Result<Map<String, Object>> run() {
        return Result.ok(syncService.syncIssues());
    }
}
