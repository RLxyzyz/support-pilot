package com.supportpilot.dto;

import java.time.Instant;

/**
 * 工单列表 / 导出的统一筛选条件。所有字段可空（空表示不限制）。
 */
public record TicketQuery(
        String status,
        String priority,
        String keyword,
        Long assigneeId,
        Long productId,
        Instant start,
        Instant end,
        Boolean overdue,
        /** 仅看未归户（customer_id 为空）的存量工单。 */
        Boolean unassigned
) {
}
