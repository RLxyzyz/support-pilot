package com.supportpilot.common;

/**
 * 状态 / 优先级 → 前端 CSS class 的映射（与原型一致）。
 * 新建工单等场景使用默认映射。
 */
public final class DisplayUtil {

    private DisplayUtil() {
    }

    public static String priorityClass(String priority) {
        if (priority == null) {
            return "p3";
        }
        return switch (priority) {
            case "P1" -> "p1";
            case "P2" -> "p2";
            case "P4" -> "p4";
            default -> "p3";
        };
    }

    public static String statusClass(String status) {
        if (status == null) {
            return "st-new";
        }
        return switch (status) {
            case "处理中" -> "st-prog";
            case "待客户" -> "st-wait";
            case "已解决" -> "st-res";
            case "已升级" -> "st-esc";
            default -> "st-new";
        };
    }
}
