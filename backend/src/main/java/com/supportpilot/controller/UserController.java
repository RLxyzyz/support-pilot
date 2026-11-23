package com.supportpilot.controller;

import com.supportpilot.common.BusinessException;
import com.supportpilot.common.Result;
import com.supportpilot.common.Role;
import com.supportpilot.common.UserContext;
import com.supportpilot.common.audit.OperationAudit;
import com.supportpilot.common.security.RequireRole;
import com.supportpilot.dto.CreateUserRequest;
import com.supportpilot.dto.UserVO;
import com.supportpilot.service.UserService;
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
 * 用户管理：列表、新增、修改、删除。新增与删除需管理员权限（Service 层校验）。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @RequireRole(Role.ADMIN)
    @GetMapping
    public Result<List<UserVO>> list() {
        return Result.ok(userService.list());
    }

    /** 当前登录用户自身信息（含 teamId / dataScope），所有已登录用户可访问。 */
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(userService.me());
    }

    /** 当前登录用户修改自身资料 / 密码（不能改角色 / 数据范围 / 工作组，服务层已限制）。 */
    @OperationAudit(module = "user", action = "update", targetType = "user", summary = "修改个人资料")
    @PutMapping("/me")
    public Result<UserVO> updateMe(@RequestBody CreateUserRequest request) {
        UserContext.CurrentUser u = UserContext.get();
        if (u == null) {
            throw new BusinessException(401, "未登录");
        }
        return Result.ok(userService.update(u.getId(), request));
    }

    @RequireRole(Role.ADMIN)
    @OperationAudit(module = "user", action = "create", summary = "创建用户")
    @PostMapping
    public Result<UserVO> create(@Valid @RequestBody CreateUserRequest request) {
        return Result.ok(userService.create(request));
    }

    @RequireRole(Role.ADMIN)
    @OperationAudit(module = "user", action = "update", targetType = "user", targetId = "#id", summary = "修改用户 #{id}")
    @PutMapping("/{id}")
    public Result<UserVO> update(@PathVariable Long id, @RequestBody CreateUserRequest request) {
        return Result.ok(userService.update(id, request));
    }

    @RequireRole(Role.ADMIN)
    @OperationAudit(module = "user", action = "delete", targetType = "user", targetId = "#id", summary = "删除用户 #{id}")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok();
    }
}
