package com.supportpilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 自助注册请求：仅用户名/密码/姓名，角色固定为 support，租户默认。 */
@Data
public class RegisterRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度需在 3-32 个字符之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在 6-64 个字符之间")
    private String password;

    @NotBlank(message = "姓名不能为空")
    private String name;
}
