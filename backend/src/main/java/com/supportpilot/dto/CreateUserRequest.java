package com.supportpilot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateUserRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "姓名不能为空")
    private String name;

    /** 通知邮件接收地址，可空（为空时回退「登录名@mail-domain」）。 */
    private String email;

    /** 角色：ADMIN / MANAGER / ENGINEER / KB_ADMIN，默认 ENGINEER（仅管理员可指定）。 */
    private String role;

    private Long tenantId;

    /** 所属工作组 id（仅管理员可指定）。 */
    private Long teamId;

    /** 数据范围：SELF / TEAM / ALL，不传按角色默认推断（仅管理员可指定）。 */
    private String dataScope;
}
