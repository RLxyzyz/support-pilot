package com.supportpilot.dto;

import lombok.Data;

/**
 * 客户联系人 VO（V2.0 EP1）。
 */
@Data
public class ContactVO {

    private Long id;
    private Long customerId;
    private String name;
    private String email;
    private String phone;
    private String title;
    private Boolean isPrimary;
    private String createdAt;
}
