package com.supportpilot.common;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当前登录用户上下文（ThreadLocal），由 AuthFilter 写入、业务层读取，请求结束清理。
 *
 * <p>V2.0 EP2：CurrentUser 额外携带 teamId / dataScope（来自 JWT），
 * 数据权限过滤与 RBAC 拦截器均从本上下文读取，避免每次请求查库。
 */
public final class UserContext {

    @Data
    @AllArgsConstructor
    public static class CurrentUser {
        private Long id;
        private String username;
        private String name;
        private Long tenantId;
        private String role;
        private Long teamId;
        private String dataScope;
    }

    private static final ThreadLocal<CurrentUser> CURRENT = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(CurrentUser user) {
        CURRENT.set(user);
    }

    public static CurrentUser get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static boolean isAdmin() {
        return hasRole(Role.ADMIN);
    }

    /** 当前用户是否拥有任一指定角色（大小写不敏感，兼容历史小写 token）。 */
    public static boolean hasRole(String... roles) {
        CurrentUser u = CURRENT.get();
        if (u == null || u.getRole() == null || roles == null) {
            return false;
        }
        String r = u.getRole().toUpperCase();
        for (String want : roles) {
            if (want != null && r.equals(want.toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    /** 当前用户所属工作组 id（未分组为 null）。 */
    public static Long getTeamId() {
        CurrentUser u = CURRENT.get();
        return u == null ? null : u.getTeamId();
    }

    /**
     * 当前用户数据范围：优先取用户显式设置的 dataScope，否则按角色默认推断。
     * 未登录兜底为 TEAM（最严格）。
     */
    public static String getDataScope() {
        CurrentUser u = CURRENT.get();
        if (u == null) {
            return Role.SCOPE_TEAM;
        }
        if (u.getDataScope() != null && !u.getDataScope().isBlank()) {
            return u.getDataScope().toUpperCase();
        }
        return Role.defaultScope(u.getRole());
    }
}
