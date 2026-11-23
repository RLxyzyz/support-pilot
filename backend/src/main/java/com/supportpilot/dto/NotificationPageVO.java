package com.supportpilot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** 站内信分页结果（V2.0 EP3）。 */
@Data
@AllArgsConstructor
public class NotificationPageVO {
    private List<NotificationVO> list;
    private long total;
}
