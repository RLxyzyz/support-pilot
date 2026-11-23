package com.supportpilot.dto;

/** 计时日历配置：工作日位图 + 工作时段 + 时区。 */
public record SlaCalendarRequest(String name, String workdays, String segments,
                                 String timezone, Boolean enabled) {
}
