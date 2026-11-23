package com.supportpilot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 工单分页 VO：{ list, total }，与前端 TicketPage 对齐。
 */
@Data
@AllArgsConstructor
public class TicketPageVO {
    private List<TicketVO> list;
    private long total;
}
