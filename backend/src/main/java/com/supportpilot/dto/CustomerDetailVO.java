package com.supportpilot.dto;

import lombok.Data;

import java.util.List;

/**
 * 客户 360 视图（V2.0 EP1）：基本信息 + 联系人 + 分页工单 + 近 5 单 + 平均 CSAT + SLA 达成率。
 */
@Data
public class CustomerDetailVO {

    private CustomerVO customer;
    private List<ContactVO> contacts;
    /** 分页工单列表（可跳转工单详情）。 */
    private TicketPageVO tickets;
    /** 近 5 条工单。 */
    private List<TicketVO> recentTickets;
    /** 平均 CSAT（1-5），无评价为 0。 */
    private double avgCsat;
    /** CSAT 评价数。 */
    private int csatCount;
    /** SLA 达成率（0-100），无已解决工单为 0。 */
    private double slaRate;
    /** 已解决工单数（SLA 达成率分母）。 */
    private int resolvedCount;
    /** 关联工单总数。 */
    private long ticketTotal;
}
