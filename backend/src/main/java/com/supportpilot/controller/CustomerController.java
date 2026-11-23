package com.supportpilot.controller;

import com.supportpilot.common.Result;
import com.supportpilot.common.Role;
import com.supportpilot.common.audit.OperationAudit;
import com.supportpilot.common.security.RequireRole;
import com.supportpilot.dto.CustomerDetailVO;
import com.supportpilot.dto.CustomerPageVO;
import com.supportpilot.dto.CustomerRequest;
import com.supportpilot.dto.CustomerVO;
import com.supportpilot.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户域接口（V2.0 EP1）。列表 / 详情全员可见；写操作（建 / 改 / 删 / 停用）由
 * Task 6 集中式拦截器按角色矩阵控制，本控制器不加角色注解以保持业务零侵入。
 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    /** 客户列表：支持名称 / 编码关键字 + 服务等级筛选，分页。 */
    @GetMapping
    public Result<CustomerPageVO> list(@RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String slaTier,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return Result.ok(customerService.page(keyword, slaTier, page, size));
    }

    /** 客户 360 详情：基本信息 + 联系人 + 分页工单 + 近 5 单 + 平均 CSAT + SLA 达成率。 */
    @GetMapping("/{id}")
    public Result<CustomerDetailVO> detail(@PathVariable Long id,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return Result.ok(customerService.detail(id, page, size));
    }

    @RequireRole({Role.ADMIN, Role.MANAGER})
    @OperationAudit(module = "customer", action = "create", summary = "创建客户")
    @PostMapping
    public Result<CustomerVO> create(@Valid @RequestBody CustomerRequest request) {
        return Result.ok(customerService.create(request));
    }

    @RequireRole({Role.ADMIN, Role.MANAGER})
    @OperationAudit(module = "customer", action = "update", targetType = "customer",
            targetId = "#id", summary = "修改客户 #{id}")
    @PutMapping("/{id}")
    public Result<CustomerVO> update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return Result.ok(customerService.update(id, request));
    }

    @RequireRole({Role.ADMIN, Role.MANAGER})
    @OperationAudit(module = "customer", action = "update", targetType = "customer",
            targetId = "#id", summary = "停用客户 #{id}")
    @PostMapping("/{id}/disable")
    public Result<CustomerVO> disable(@PathVariable Long id) {
        return Result.ok(customerService.disable(id));
    }

    @RequireRole({Role.ADMIN, Role.MANAGER})
    @OperationAudit(module = "customer", action = "delete", targetType = "customer",
            targetId = "#id", summary = "删除客户 #{id}")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return Result.ok();
    }
}
