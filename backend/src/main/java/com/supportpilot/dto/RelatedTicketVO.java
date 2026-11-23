package com.supportpilot.dto;

import lombok.Data;

/** 关联工单摘要（详情页「关联工单」区展示）。 */
@Data
public class RelatedTicketVO {
    private Long relationId;   // 关联记录 id（用于取消关联）
    private String id;         // 工单 id，带 # 前缀
    private String title;
    private String status;
    private String relationType;
}
