package com.supportpilot.dto;

import lombok.Data;

import java.util.List;

/**
 * 工单 VO，与前端 Ticket 对齐。id 已补 # 前缀，tenant / product 为名称。
 */
@Data
public class TicketVO {

    private String id;
    private String title;
    private String tenant;
    private String product;
    private String priority;
    private String priorityClass;
    private String status;
    private String statusClass;
    private String category;
    private String sla;
    private Integer slaPct;
    private String slaColor;
    /** V2.1 SLA 快照：NORMAL / WARNING / BREACHED / PAUSED / STOPPED / MET。 */
    private String slaState;
    private String slaSource;
    private String slaCalendarType;
    private String slaResolveDueAt;
    private String slaFirstResponseDueAt;
    private String updateTime;
    private Long assigneeId;
    private String assigneeName;
    private String assigneeUsername;
    private String env;
    private String createdAt;
    private String firstResponseAt;
    private String resolvedAt;
    private Integer csatScore;
    private String csatComment;
    private String csatAt;
    // 客户 360 信息块（V2.0 EP1）：供工单详情右侧客户卡片渲染
    private Long customerId;
    private String customerName;
    private String customerCode;
    private String slaTier;
    private Long contactId;
    private String contactName;
    private String contactEmail;
    private String contactPhone;
    private String contactTitle;
    private Boolean contactIsPrimary;
    private List<RelatedTicketVO> relations;
    private String body;
    private String code;
    private List<TicketMessageVO> messages;
    /** 工单主体附件（未绑定到具体消息的附件）。 */
    private List<AttachmentVO> attachments;

    // ===== V2.3 智能服务 =====
    /** 相似工单组 id；null 表示未归组。 */
    private Long similarityGroupId;
    /** 组内相似工单数（含自身）。 */
    private Integer similarCount;
    /** L0 自助状态：AUTO_REPLIED / TRANSFERRED / CONFIRMED / REOPENED。 */
    private String l0State;
    /** L0 被重开次数。 */
    private Integer l0ReopenCount;
    /** 最近一条消息的情绪：POSITIVE / NEUTRAL / NEGATIVE；null 表示未分析。 */
    private String sentiment;
    /** M6 会话摘要（人工修正版优先）；null 表示未生成。 */
    private String summary;
}
