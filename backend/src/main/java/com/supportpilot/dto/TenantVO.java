package com.supportpilot.dto;

import lombok.Data;

/** 租户下拉选项（新建工单选择客户用）。 */
@Data
public class TenantVO {
    private Long id;
    private String name;
    private String code;
}
