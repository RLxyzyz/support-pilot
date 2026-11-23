package com.supportpilot.dto;

import lombok.Data;

/** 快捷回复模板 VO。 */
@Data
public class ReplyTemplateVO {
    private Long id;
    private String name;
    private String content;
    private String category;
    private String createdAt;
}
