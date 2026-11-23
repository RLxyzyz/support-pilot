package com.supportpilot.dto;

import lombok.Data;

/** 邮件任务视图对象（V2.0 EP3）：管理端查看失败任务与人工重发。 */
@Data
public class MailTaskVO {
    private Long id;
    private Long notificationId;
    private String toAddr;
    private String subject;
    /** pending / sent / failed */
    private String status;
    private Integer retryCount;
    private String lastError;
    private String createdAt;
    private String updatedAt;
}
