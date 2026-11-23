package com.supportpilot.service;

import com.supportpilot.dto.ProductVO;
import com.supportpilot.dto.TenantVO;
import com.supportpilot.entity.Product;
import com.supportpilot.entity.Tenant;
import com.supportpilot.repository.ProductRepository;
import com.supportpilot.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 基础数据查询：租户、产品列表，供前端下拉选择。
 * 新建工单时客户/产品不再写死默认值，由用户在前端选择。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReferenceService {

    private final TenantRepository tenantRepository;
    private final ProductRepository productRepository;

    public List<TenantVO> listTenants() {
        return tenantRepository.findAll().stream().map(this::toTenantVO).toList();
    }

    /** tenantId 为空返回全部产品，否则只返回该租户下的产品。 */
    public List<ProductVO> listProducts(Long tenantId) {
        List<Product> all = productRepository.findAll();
        if (tenantId != null) {
            all = all.stream().filter(p -> tenantId.equals(p.getTenantId())).toList();
        }
        return all.stream().map(this::toProductVO).toList();
    }

    private TenantVO toTenantVO(Tenant t) {
        TenantVO vo = new TenantVO();
        vo.setId(t.getId());
        vo.setName(t.getName());
        vo.setCode(t.getCode());
        return vo;
    }

    private ProductVO toProductVO(Product p) {
        ProductVO vo = new ProductVO();
        vo.setId(p.getId());
        vo.setTenantId(p.getTenantId());
        vo.setName(p.getName());
        return vo;
    }
}
