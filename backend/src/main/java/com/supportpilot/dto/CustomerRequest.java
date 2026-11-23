package com.supportpilot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/**
 * 客户创建 / 修改请求（V2.0 EP1）。code 租户内唯一由 Service 校验。
 */
@Data
public class CustomerRequest {

    @NotBlank(message = "客户名称不能为空")
    private String name;

    @NotBlank(message = "客户编码不能为空")
    private String code;

    /** 服务等级：金牌 / 银牌 / 铜牌 / 默认，不传走默认。 */
    private String slaTier;

    private String contractNo;
    private LocalDate contractStart;
    private LocalDate contractEnd;
    /** active / disabled，不传默认 active。 */
    private String status;
}
