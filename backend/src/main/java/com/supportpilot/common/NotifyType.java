package com.supportpilot.common;

import java.util.Map;

/**
 * 通知事件类型常量（V2.0 EP3）。
 *
 * <p>P0 实际产生的事件：工单分派（TICKET_ASSIGNED）与转派（TICKET_TRANSFERRED）；
 * SLA 预警 / 超时与审批类事件类型预先定义，供 V2.1 事件源接入。
 */
public final class NotifyType {

    public static final String TICKET_ASSIGNED = "TICKET_ASSIGNED";
    public static final String TICKET_TRANSFERRED = "TICKET_TRANSFERRED";
    public static final String SLA_WARNING = "SLA_WARNING";
    public static final String SLA_BREACH = "SLA_BREACH";
    public static final String APPROVAL_TODO = "APPROVAL_TODO";
    public static final String APPROVAL_RESULT = "APPROVAL_RESULT";
    /** V2.3 M2 故障风暴告警。 */
    public static final String STORM_ALERT = "STORM_ALERT";
    /** V2.3 M5 负面情绪告警。 */
    public static final String SENTIMENT_ALERT = "SENTIMENT_ALERT";
    /** V2.3 M7 客户健康分告警。 */
    public static final String HEALTH_ALERT = "HEALTH_ALERT";

    /** 事件类型的展示名，用于通知规则管理页与站内信标题前缀。 */
    public static final Map<String, String> LABELS = Map.of(
            TICKET_ASSIGNED, "工单分派",
            TICKET_TRANSFERRED, "工单转派",
            SLA_WARNING, "SLA 预警",
            SLA_BREACH, "SLA 超时",
            APPROVAL_TODO, "审批待办",
            APPROVAL_RESULT, "审批结果",
            STORM_ALERT, "故障风暴",
            SENTIMENT_ALERT, "负面情绪",
            HEALTH_ALERT, "客户健康"
    );

    /** 接收人标识：当前事件关联人（分派/转派时为被分派人）。 */
    public static final String RECEIVER_ASSIGNEE = "ASSIGNEE";
    /** 接收人标识：所有主管（role=MANAGER）。 */
    public static final String RECEIVER_MANAGER = "MANAGER";

    private NotifyType() {
    }

    public static String label(String type) {
        return LABELS.getOrDefault(type, type);
    }
}
