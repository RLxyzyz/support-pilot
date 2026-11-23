package com.supportpilot.controller;

import com.supportpilot.common.Result;
import com.supportpilot.common.Role;
import com.supportpilot.common.audit.OperationAudit;
import com.supportpilot.common.security.RequireRole;
import com.supportpilot.dto.ContactRequest;
import com.supportpilot.dto.ContactVO;
import com.supportpilot.service.ContactService;
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
 * 客户联系人接口（V2.0 EP1），嵌套在客户资源下。
 * 写操作由 Task 6 拦截器统一控制角色。
 */
@RestController
@RequestMapping("/api/customers/{customerId}/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;

    @GetMapping
    public Result<List<ContactVO>> list(@PathVariable Long customerId) {
        return Result.ok(contactService.list(customerId));
    }

    @RequireRole({Role.ADMIN, Role.MANAGER})
    @OperationAudit(module = "contact", action = "create",
            targetId = "#customerId", summary = "新增客户 #{customerId} 联系人")
    @PostMapping
    public Result<ContactVO> create(@PathVariable Long customerId,
                                    @Valid @RequestBody ContactRequest request) {
        return Result.ok(contactService.create(customerId, request));
    }

    @RequireRole({Role.ADMIN, Role.MANAGER})
    @OperationAudit(module = "contact", action = "update", targetType = "contact",
            targetId = "#id", summary = "修改联系人 #{id}")
    @PutMapping("/{id}")
    public Result<ContactVO> update(@PathVariable Long customerId,
                                    @PathVariable Long id,
                                    @Valid @RequestBody ContactRequest request) {
        return Result.ok(contactService.update(customerId, id, request));
    }

    @RequireRole({Role.ADMIN, Role.MANAGER})
    @OperationAudit(module = "contact", action = "update", targetType = "contact",
            targetId = "#id", summary = "设联系人 #{id} 为主联系人")
    @PostMapping("/{id}/primary")
    public Result<ContactVO> setPrimary(@PathVariable Long customerId, @PathVariable Long id) {
        return Result.ok(contactService.setPrimary(customerId, id));
    }

    @RequireRole({Role.ADMIN, Role.MANAGER})
    @OperationAudit(module = "contact", action = "delete", targetType = "contact",
            targetId = "#id", summary = "删除联系人 #{id}")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long customerId, @PathVariable Long id) {
        contactService.delete(customerId, id);
        return Result.ok();
    }
}
