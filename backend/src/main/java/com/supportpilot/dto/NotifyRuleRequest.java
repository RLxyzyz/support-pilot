package com.supportpilot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 创建 / 编辑通知规则请求（V2.0 EP3）。 */
@Data
public class NotifyRuleRequest {

    @NotBlank(message = "事件类型不能为空")
    private String eventType;

    private String name;

    private String conditionJson;

    @NotBlank(message = "接收人不能为空")
    private String receivers;

    private Boolean inAppEnabled;

    private Boolean emailEnabled;

    private Boolean enabled;
}
