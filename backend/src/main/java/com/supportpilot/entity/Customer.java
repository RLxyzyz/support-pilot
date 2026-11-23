package com.supportpilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 客户（V2.0 EP1）。code 租户内唯一；sla_tier 服务等级枚举（金牌/银牌/铜牌/默认）；status active/disabled。
 */
@Data
@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    private String name;

    /** 客户编码，租户内唯一。 */
    private String code;

    /** 服务等级：金牌 / 银牌 / 铜牌 / 默认。默认值避免写入 NULL 违反 NOT NULL。 */
    @Column(name = "sla_tier")
    private String slaTier = "默认";

    @Column(name = "contract_no")
    private String contractNo;

    @Column(name = "contract_start")
    private LocalDate contractStart;

    @Column(name = "contract_end")
    private LocalDate contractEnd;

    /** active / disabled。已关联工单的客户禁止物理删除，仅置为 disabled。 */
    private String status = "active";

    @Column(name = "created_at")
    private Instant createdAt;
}
