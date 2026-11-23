package com.supportpilot.dto;

import lombok.Data;

/** 通知规则视图对象（V2.0 EP3）。 */
@Data
public class NotifyRuleVO {
    private Long id;
    private Long tenantId;
    private String eventType;
    private String eventLabel;
    private String name;
    private String conditionJson;
    private String receivers;
    private Boolean inAppEnabled;
    private Boolean emailEnabled;
    private Boolean enabled;
    private String createdAt;
}
