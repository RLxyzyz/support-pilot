package com.supportpilot.dto;

import lombok.Data;

/** AI 分诊建议：优先级 / 分类 / 处理人（按负载）+ 依据。 */
@Data
public class TriageVO {
    private String priority;
    private String category;
    private Long assigneeId;
    private String assigneeName;
    private String reasoning;
}
