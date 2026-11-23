package com.supportpilot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 客户分页 VO：{ list, total }。
 */
@Data
@AllArgsConstructor
public class CustomerPageVO {
    private List<CustomerVO> list;
    private long total;
}
