package com.supportpilot.dto;

import lombok.Data;

/**
 * 知识库文档 VO，与前端 KbDoc 对齐。
 */
@Data
public class KbDocVO {
    private Long id;
    private String name;
    private String path;
    private String productLine;
    private Integer chunks;
    private Integer hits;
    private String status;
}
