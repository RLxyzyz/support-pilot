package com.supportpilot.controller;

import com.supportpilot.common.Result;
import com.supportpilot.common.Role;
import com.supportpilot.common.audit.OperationAudit;
import com.supportpilot.common.security.RequireRole;
import com.supportpilot.dto.TeamRequest;
import com.supportpilot.dto.TeamVO;
import com.supportpilot.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工作组接口（V2.0 EP2）。写操作由 RequireRole 拦截器（Task 6）+ 服务层 requireAdmin 双重保证；
 * 列表全员可读。
 */
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @GetMapping
    public Result<List<TeamVO>> list() {
        return Result.ok(teamService.list());
    }

    @GetMapping("/{id}")
    public Result<TeamVO> get(@PathVariable Long id) {
        return Result.ok(teamService.get(id));
    }

    @RequireRole(Role.ADMIN)
    @OperationAudit(module = "team", action = "create", summary = "新建工作组")
    @PostMapping
    public Result<TeamVO> create(@Valid @RequestBody TeamRequest request) {
        return Result.ok(teamService.create(request));
    }

    @RequireRole(Role.ADMIN)
    @OperationAudit(module = "team", action = "update", targetType = "team",
            targetId = "#id", summary = "修改工作组 #{id}")
    @PutMapping("/{id}")
    public Result<TeamVO> update(@PathVariable Long id, @Valid @RequestBody TeamRequest request) {
        return Result.ok(teamService.update(id, request));
    }

    @RequireRole(Role.ADMIN)
    @OperationAudit(module = "team", action = "disable", targetType = "team",
            targetId = "#id", summary = "停用工作组 #{id}")
    @PostMapping("/{id}/disable")
    public Result<TeamVO> disable(@PathVariable Long id) {
        return Result.ok(teamService.disable(id));
    }
}
