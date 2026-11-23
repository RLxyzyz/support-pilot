package com.supportpilot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** 知识库文档分页结果。 */
@Data
@AllArgsConstructor
public class KbDocPageVO {
    private List<KbDocVO> list;
    private long total;
    private int page;
    private int size;
    /** 全部产品线，用于前端筛选下拉（不受分页影响）。 */
    private List<String> productLines;
}
