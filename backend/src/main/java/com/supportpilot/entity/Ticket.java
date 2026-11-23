package com.supportpilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "ticket")
public class Ticket {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    /** 所属客户 id（customer.id）。null 表示未挂客户（存量数据 / 非人工创建，V2.0 EP1）。 */
    @Column(name = "customer_id")
    private Long customerId;

    /** 工单联系人 id（contact.id，V2.0 EP1）。 */
    @Column(name = "contact_id")
    private Long contactId;

    private String title;

    @Column(columnDefinition = "text")
    private String body;

    @Column(columnDefinition = "text")
    private String code;

    private String priority;

    @Column(name = "priority_class")
    private String priorityClass;

    private String status;

    @Column(name = "status_class")
    private String statusClass;

    private String category;

    private String sla;

    @Column(name = "sla_pct")
    private Integer slaPct;

    @Column(name = "sla_color")
    private String slaColor;

    @Column(name = "update_time")
    private String updateTime;

    /** 工单来源：seed（种子数据）/ github（GitHub Issue 同步）等，便于多数据源接入与幂等去重。 */
    private String source;

    /** 来源侧的唯一引用（如 GitHub 的 owner/repo#number），与 source 联合唯一。 */
    @Column(name = "source_ref")
    private String sourceRef;

    /** 当前处理人用户主键（app_user.id），接单/转派时写入。null 表示未分配。 */
    @Column(name = "assignee_id")
    private Long assigneeId;

    /** 环境描述：生产/K8s/测试/本地 等，用于详情 meta 展示。 */
    private String env;

    /** 上次从 GitHub 同步时该 issue 的 updated_at（ISO-8601），用于增量同步判断是否有更新。null 表示非 GitHub 来源。 */
    @Column(name = "github_updated_at")
    private String githubUpdatedAt;

    /** 首次响应时间：接单（accept）时若为空则写入，用于 FRT 指标。 */
    @Column(name = "first_response_at")
    private Instant firstResponseAt;

    /** 解决时间：状态置为「已解决」时写入；从「已解决」改回时清空。用于 MTTR / SLA 达成率。 */
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** SLA 累计暂停秒数：状态处于「待客户」期间不计时，离开时累计到此处。默认 0，避免新建工单写入 NULL 违反 NOT NULL 约束。 */
    @Column(name = "sla_paused_seconds")
    private Long slaPausedSeconds = 0L;

    /** 当前进入「待客户」暂停的起点；离开「待客户」时据此结算并清空。 */
    @Column(name = "sla_paused_at")
    private Instant slaPausedAt;

    /** 客户满意度评分（1-5），null 表示未评价。 */
    @Column(name = "csat_score")
    private Integer csatScore;

    /** 满意度评语。 */
    @Column(name = "csat_comment")
    private String csatComment;

    /** 评价时间。 */
    @Column(name = "csat_at")
    private Instant csatAt;

    /** 父工单 id（合并 / 父子关联），null 表示无父工单。 */
    @Column(name = "parent_id")
    private Long parentId;

    // ===== V2.3 智能服务 =====

    /** 相似工单组 id（组内最小工单号），null 表示未归组。 */
    @Column(name = "similarity_group_id")
    private Long similarityGroupId;

    /** L0 自助状态：AUTO_REPLIED / TRANSFERRED / CONFIRMED / REOPENED。 */
    @Column(name = "l0_state", length = 24)
    private String l0State;

    /** L0 关闭时间（确认解决）。 */
    @Column(name = "l0_closed_at")
    private Instant l0ClosedAt;

    /** 客户确认解决的一次性 token（72 小时有效）。 */
    @Column(name = "l0_confirm_token", length = 64)
    private String l0ConfirmToken;

    /** L0 被客户重开次数。 */
    @Column(name = "l0_reopen_count")
    private Integer l0ReopenCount = 0;

    /** L0 自动应答的助手名称。 */
    @Column(name = "l0_handler_name", length = 64)
    private String l0HandlerName;

    // ===== V2.1 SLA 快照（FR-S02 / FR-S03 / FR-S11）=====

    /** 创建时命中的策略行 id；null 表示走 yml 全局回落。 */
    @Column(name = "sla_policy_id")
    private Long slaPolicyId;

    /** 首响到期时间（创建时快照，不随策略变更）。 */
    @Column(name = "first_response_due_at")
    private Instant firstResponseDueAt;

    /** 解决到期时间（创建时快照，不随策略变更）。 */
    @Column(name = "resolve_due_at")
    private Instant resolveDueAt;

    /** RUNNING / WARNING / BREACHED / MET / STOPPED。 */
    @Column(name = "sla_state", length = 16)
    private String slaState;

    /** 快照时的计时日历类型：NATURAL / WORKTIME。 */
    @Column(name = "sla_calendar_type", length = 16)
    private String slaCalendarType;

    /** 快照时的计时日历 id（WORKTIME 时用于跳过非工作时段）。 */
    @Column(name = "sla_calendar_id")
    private Long slaCalendarId;

    /** 快照时的预警阈值（百分比）。 */
    @Column(name = "sla_warning_threshold")
    private Integer slaWarningThreshold;

    /** 预警发送时间；非空表示已发过，扫描任务据此幂等。 */
    @Column(name = "sla_warning_sent_at")
    private Instant slaWarningSentAt;

    /** 违约时间；非空表示已超时，扫描任务据此幂等。 */
    @Column(name = "sla_breached_at")
    private Instant slaBreachedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @OneToMany(mappedBy = "ticket", fetch = FetchType.LAZY)
    @OrderBy("id asc")
    private List<TicketMessage> messages = new ArrayList<>();
}
