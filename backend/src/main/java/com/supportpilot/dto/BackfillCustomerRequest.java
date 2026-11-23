package com.supportpilot.dto;

import lombok.Data;

import java.util.List;

/**
 * 存量工单批量归户请求（V2.0 EP1 / PRD EX-03、RK-01）。
 * 仅对 customer_id 为空的工单生效，已挂客户的工单计入 skipped，避免误覆盖。
 */
@Data
public class BackfillCustomerRequest {
    /** 待归户工单 ID 列表。 */
    private List<Long> ticketIds;
    /** 目标客户 ID。 */
    private Long customerId;
    /** 目标联系人 ID（选填，必须属于目标客户）。 */
    private Long contactId;
}
