package com.supportpilot.dto;

import lombok.Data;

/**
 * 用户视图对象，不含密码。
 * V2.0 EP2 增加 teamId / dataScope，供前端用户管理与个人设置展示。
 */
@Data
public class UserVO {
    private Long id;
    private String username;
    private String name;
    /** 通知邮件地址；为空时邮件回退「登录名@mail-domain」。 */
    private String email;
    private String role;
    private Long tenantId;
    private Long teamId;
    private String dataScope;
}
