package com.supportpilot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class OperationLogPageVO {
    private List<OperationLogVO> list;
    private long total;
}
