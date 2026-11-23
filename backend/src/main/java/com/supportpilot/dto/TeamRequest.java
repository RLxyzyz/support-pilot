package com.supportpilot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 创建 / 编辑工作组请求（V2.0 EP2）。状态通过 disable 接口控制，不在此处传。 */
@Data
public class TeamRequest {
    @NotBlank(message = "工作组名称不能为空")
    private String name;
}
