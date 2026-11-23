package com.supportpilot.dto;

/** 新增工单关联。relatedTicketId 为对方工单号（数字 id），relationType 默认 related。 */
public record TicketRelationRequest(Long relatedTicketId, String relationType) {
}
