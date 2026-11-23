package com.supportpilot.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 客户 VO（V2.0 EP1）。列表与详情共用；ticketCount 用于列表展示关联工单数。
 */
@Data
public class CustomerVO {

    private Long id;
    private Long tenantId;
    private String name;
    private String code;
    /** 服务等级：金牌 / 银牌 / 铜牌 / 默认。 */
    private String slaTier;
    private String contractNo;
    private LocalDate contractStart;
    private LocalDate contractEnd;
    /** active / disabled。 */
    private String status;
    private String createdAt;
    /** 关联工单总数（列表展示用）。 */
    private long ticketCount;
}
