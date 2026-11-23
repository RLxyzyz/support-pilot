package com.supportpilot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Agent 步骤 VO，SSE step 事件载荷，与前端 AgentStep 对齐。
 */
@Data
@AllArgsConstructor
public class AgentStepVO {
    private String tag;
    private String cls;
    private String time;
    private String html;
}
