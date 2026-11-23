package com.supportpilot.common;

import java.util.Set;

/**
 * 角色与数据权限常量（V2.0 EP2）。
 *
 * <p>角色统一大写：ADMIN / MANAGER / ENGINEER / KB_ADMIN。
 * 数据范围：SELF（仅本人）/ TEAM（本人 + 本组）/ ALL（全部）。
 * 存量 V1.x 的 admin/engineer/support 由 schema 迁移映射为 ADMIN/ENGINEER。
 */
public final class Role {

    public static final String ADMIN = "ADMIN";
    public static final String MANAGER = "MANAGER";
    public static final String ENGINEER = "ENGINEER";
    public static final String KB_ADMIN = "KB_ADMIN";

    public static final String SCOPE_SELF = "SELF";
    public static final String SCOPE_TEAM = "TEAM";
    public static final String SCOPE_ALL = "ALL";

    private static final Set<String> VALID_ROLES = Set.of(ADMIN, MANAGER, ENGINEER, KB_ADMIN);
    private static final Set<String> VALID_SCOPES = Set.of(SCOPE_SELF, SCOPE_TEAM, SCOPE_ALL);

    private Role() {
    }

    /** 归一化为大写；null / 空 → ENGINEER。 */
    public static String normalize(String role) {
        if (role == null || role.isBlank()) {
            return ENGINEER;
        }
        return role.toUpperCase();
    }

    public static boolean isValidRole(String role) {
        return role != null && VALID_ROLES.contains(role.toUpperCase());
    }

    public static boolean isValidScope(String scope) {
        return scope != null && VALID_SCOPES.contains(scope.toUpperCase());
    }

    /**
     * 按角色推断默认数据范围：ADMIN / MANAGER → ALL，其余（含 ENGINEER / KB_ADMIN）→ TEAM。
     * 演示模式（tid=null）下的 ALL 放宽在数据权限过滤层处理。
     */
    public static String defaultScope(String role) {
        String r = normalize(role);
        return (r.equals(ADMIN) || r.equals(MANAGER)) ? SCOPE_ALL : SCOPE_TEAM;
    }
}
