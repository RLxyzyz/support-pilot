package com.supportpilot.dto;

/** SLA 策略矩阵单行（服务等级 × 优先级）。 */
public record SlaPolicyRequest(String tier, String priority, Integer firstResponseMinutes,
                               Integer resolveMinutes, String calendarType, Long calendarId,
                               Integer warningThreshold, Boolean enabled) {
}
