package com.supportpilot.dto;

import lombok.Data;

/**
 * 工单会话消息 VO，与前端 TicketMessage 对齐。
 */
@Data
public class TicketMessageVO {
    private Long id;
    private String who;
    private String role;
    private String text;
    private boolean internal;
    private java.util.List<AttachmentVO> attachments;
    /** M5 情绪：POSITIVE / NEUTRAL / NEGATIVE；null 表示未分析。 */
    private String sentiment;
    /** M5 情绪强度 0-1。 */
    private Double sentimentIntensity;
}
