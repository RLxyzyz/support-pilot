package com.supportpilot.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式接口级角色鉴权（V2.0 EP2 / FR-A01）。
 *
 * <p>标注在 Controller 方法或类上，由 {@link com.supportpilot.config.RoleInterceptor} 在
 * 业务方法执行前统一校验：当前用户角色命中任一指定角色即放行，否则返回 403 并写越权审计。
 * 与 AuthFilter 分层：Filter 做认证（401），Interceptor 做鉴权（403）。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /** 允许访问的角色（任一命中即可），使用 {@link com.supportpilot.common.Role} 常量。 */
    String[] value();
}
