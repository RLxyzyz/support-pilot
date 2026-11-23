package com.supportpilot.dto;

import lombok.Data;

/** 站内信视图对象（V2.0 EP3）。 */
@Data
public class NotificationVO {
    private Long id;
    private String type;
    /** 事件类型展示名。 */
    private String typeLabel;
    private String title;
    private String content;
    private String refType;
    private String refId;
    private Boolean isRead;
    private String readAt;
    private String createdAt;
}
