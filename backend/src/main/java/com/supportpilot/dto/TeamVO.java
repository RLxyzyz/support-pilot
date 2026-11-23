package com.supportpilot.dto;

import lombok.Data;

/** 工作组视图对象（V2.0 EP2）。 */
@Data
public class TeamVO {
    private Long id;
    private String name;
    private String status;
    private Long memberCount;
    private String createdAt;
}
