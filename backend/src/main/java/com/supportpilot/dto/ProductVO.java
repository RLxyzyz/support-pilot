package com.supportpilot.dto;

import lombok.Data;

/** 产品下拉选项（新建工单选择产品用），带 tenantId 便于按客户过滤。 */
@Data
public class ProductVO {
    private Long id;
    private Long tenantId;
    private String name;
}
