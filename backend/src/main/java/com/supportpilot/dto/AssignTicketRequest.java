package com.supportpilot.dto;

/** 转派工单：指定目标处理人用户 ID。 */
public record AssignTicketRequest(Long assigneeId) {
}
