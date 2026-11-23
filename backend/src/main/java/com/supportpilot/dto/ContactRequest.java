package com.supportpilot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 联系人创建 / 修改请求（V2.0 EP1）。同客户内 email 唯一由 Service 校验。
 */
@Data
public class ContactRequest {

    @NotBlank(message = "联系人姓名不能为空")
    private String name;

    private String email;
    private String phone;
    private String title;
    /** 是否设为主联系人。设为 true 时该客户旧主联系人自动取消。 */
    private Boolean isPrimary;
}
