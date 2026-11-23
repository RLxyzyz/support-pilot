package com.supportpilot.controller;

import com.supportpilot.common.Result;
import com.supportpilot.dto.ProductVO;
import com.supportpilot.dto.TenantVO;
import com.supportpilot.service.ReferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 基础数据查询：客户（租户）、产品下拉列表。 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReferenceController {

    private final ReferenceService referenceService;

    /** 全部客户（租户）列表，用于新建工单选择客户。 */
    @GetMapping("/tenants")
    public Result<List<TenantVO>> tenants() {
        return Result.ok(referenceService.listTenants());
    }

    /** 产品列表；可选 tenantId 按客户过滤，不传返回全部。 */
    @GetMapping("/products")
    public Result<List<ProductVO>> products(@RequestParam(required = false) Long tenantId) {
        return Result.ok(referenceService.listProducts(tenantId));
    }
}
