package com.supportpilot.common;

/**
 * 当前请求的租户上下文（ThreadLocal）。
 * null 表示未指定租户（演示模式，查询不过滤租户，展示全部数据）。
 */
public final class TenantContext {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static Long get() {
        return TENANT_ID.get();
    }

    public static void clear() {
        TENANT_ID.remove();
    }
}
