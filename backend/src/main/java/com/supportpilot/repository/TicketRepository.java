package com.supportpilot.repository;

import com.supportpilot.entity.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    /**
     * 详情查询：一次性 fetch join 租户、产品、会话消息，避免 open-in-view=false 下的懒加载异常。
     */
    @Query("select t from Ticket t " +
            "left join fetch t.tenant " +
            "left join fetch t.product " +
            "left join fetch t.messages " +
            "where t.id = :id")
    Optional<Ticket> findDetailById(@Param("id") Long id);

    /**
     * 取最大 id，用于生成新工单号。
     */
    Optional<Ticket> findTopByOrderByIdDesc();

    /**
     * 按来源 + 来源引用查工单，用于外部数据同步时的幂等去重。
     */
    Optional<Ticket> findBySourceAndSourceRef(String source, String sourceRef);

    /** 统计指定租户（tid 为 null 表示全部）的工单总数。 */
    @Query("select count(t) from Ticket t where :tid is null or t.tenant.id = :tid")
    long countAll(@Param("tid") Long tid);

    /** 统计指定租户下某状态的工单数。 */
    @Query("select count(t) from Ticket t where (:tid is null or t.tenant.id = :tid) and t.status = :status")
    long countByStatus(@Param("tid") Long tid, @Param("status") String status);

    /** 按状态查工单列表（用于批量同步已解决工单进知识库）。 */
    List<Ticket> findByStatus(String status);

    /** 统计指定租户下某优先级且状态不等于指定值的工单数（用于 P1 待处理等）。 */
    @Query("select count(t) from Ticket t where (:tid is null or t.tenant.id = :tid) " +
            "and t.priority = :priority and t.status <> :status")
    long countByPriorityAndStatusNot(@Param("tid") Long tid, @Param("priority") String priority,
                                     @Param("status") String status);

    /** 某处理人未结工单数（状态不在排除列表），用于 AI 分诊按负载分配处理人。 */
    long countByAssigneeIdAndStatusNotIn(Long assigneeId, List<String> statuses);

    /** 各处理人的未结工单数，返回 [assignee_id, count]，用于新建工单自动派单均分。 */
    @Query("select t.assigneeId, count(t) from Ticket t where t.assigneeId is not null " +
            "and t.status not in :statuses group by t.assigneeId")
    List<Object[]> countOpenByAssignee(@Param("statuses") List<String> statuses);

    // ===== V2.0 EP1 客户 360 聚合查询 =====
    /** 客户下分页工单（详情 360 视图用）。 */
    Page<Ticket> findByCustomerId(Long customerId, Pageable pageable);

    /** 客户下全部工单（计算平均 CSAT / SLA 达成率用）。 */
    List<Ticket> findByCustomerId(Long customerId);

    /** 客户近 5 条工单。 */
    List<Ticket> findTop5ByCustomerIdOrderByIdDesc(Long customerId);

    /** 客户关联工单总数（删除守卫 + 列表展示用）。 */
    long countByCustomerId(Long customerId);

    /** 批量统计多个客户的关联工单数：返回 [customer_id, count]，避免列表 N+1。 */
    @Query(value = "select customer_id, count(*) from ticket where customer_id in (:ids) group by customer_id",
            nativeQuery = true)
    List<Object[]> countByCustomerIdIn(@Param("ids") List<Long> ids);

    /**
     * 修正历史 GitHub 工单的产品归属：把仍挂在演示/默认 Spring Boot 产品上的同步工单
     * 改为实际仓库产品。只动 Spring Boot 演示产品，避免覆盖人工调整过的产品。
     */
    @Modifying
    @Query(value = "update ticket set product_id = :productId " +
            "where source = 'github' and source_ref like concat(:prefix, '%') " +
            "and product_id in (select id from product where name like 'Spring Boot%')", nativeQuery = true)
    int fixGithubTicketProduct(@Param("prefix") String prefix, @Param("productId") Long productId);

    /**
     * 近 N 天每天新建工单数（MySQL 原生按天聚合），返回 [日期字符串 yyyy-MM-dd, 数量]。
     */
    @Query(value = "select DATE_FORMAT(created_at, '%Y-%m-%d') as d, count(*) as c from ticket " +
            "where (:tid is null or tenant_id = :tid) and created_at >= :since " +
            "group by DATE_FORMAT(created_at, '%Y-%m-%d') order by d asc", nativeQuery = true)
    List<Object[]> countNewPerDay(@Param("tid") Long tid, @Param("since") Instant since);

    /** 范围内已解决的工单（resolved_at 落在 [since, until]），用于 MTTR / SLA 达成率。 */
    @Query("select t from Ticket t where (:tid is null or t.tenant.id = :tid) " +
            "and t.resolvedAt between :since and :until")
    List<Ticket> findResolvedInRange(@Param("tid") Long tid, @Param("since") Instant since,
                                     @Param("until") Instant until);

    /** 范围内产生首次响应的工单（first_response_at 落在 [since, until]），用于 FRT。 */
    @Query("select t from Ticket t where (:tid is null or t.tenant.id = :tid) " +
            "and t.firstResponseAt between :since and :until")
    List<Ticket> findRespondedInRange(@Param("tid") Long tid, @Param("since") Instant since,
                                      @Param("until") Instant until);

    /** 范围内按分类分组统计工单量（取前 12），返回 [category, count]。 */
    @Query(value = "select category, count(*) from ticket " +
            "where (:tid is null or tenant_id = :tid) and created_at >= :since and created_at < :until " +
            "group by category order by count(*) desc limit 12", nativeQuery = true)
    List<Object[]> categoryDist(@Param("tid") Long tid, @Param("since") Instant since,
                                @Param("until") Instant until);

    /** 范围内按处理人分组统计工单量 Top5，返回 [assignee_id, count]。 */
    @Query(value = "select assignee_id, count(*) from ticket " +
            "where (:tid is null or tenant_id = :tid) and created_at >= :since and created_at < :until " +
            "and assignee_id is not null group by assignee_id order by count(*) desc limit 5", nativeQuery = true)
    List<Object[]> assigneeLoad(@Param("tid") Long tid, @Param("since") Instant since,
                                @Param("until") Instant until);

    // ===== V2.1 服务履约升级 =====

    /** 认领仲裁：行级悲观锁，保证并发下只有一人成功（FR-C03）。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.id = :id")
    Optional<Ticket> findByIdForUpdate(@Param("id") Long id);

    /** SLA 扫描候选：指定状态、未解决/未升级/未待客户的工单。 */
    @Query("select t from Ticket t where t.slaState in :states " +
            "and t.status not in ('已解决','已升级','待客户') order by t.id asc")
    List<Ticket> findSlaCandidates(@Param("states") List<String> states);

    /** 存量补算候选：未解决、尚无到期快照的工单（FR-S05）。 */
    @Query("select t from Ticket t where t.status not in ('已解决','已升级') " +
            "and t.resolveDueAt is null and t.createdAt is not null order by t.id asc")
    List<Ticket> findBackfillCandidates(Pageable pageable);

    /** 未解决工单（策略变更后重算）。 */
    List<Ticket> findByStatusNotIn(List<String> statuses);

    // ===== V2.3 智能服务 =====

    /** 近期同租户工单，作为相似检测候选集。 */
    @Query("select t from Ticket t where (:tid is null or t.tenant.id = :tid) " +
            "and t.createdAt >= :since order by t.id desc")
    List<Ticket> findRecentForSimilarity(@Param("tid") Long tid, @Param("since") Instant since);

    /** 近窗口内按分类统计新建工单数（风暴识别），返回 [category, count]。 */
    @Query(value = "select category, count(*) from ticket " +
            "where created_at >= :since and (:tid is null or tenant_id = :tid) " +
            "group by category order by count(*) desc", nativeQuery = true)
    List<Object[]> countByCategorySince(@Param("tid") Long tid, @Param("since") Instant since);

    /** 某分类最新工单（风暴记录 last_ticket_id / 事件单参考）。 */
    Optional<Ticket> findFirstByCategoryOrderByIdDesc(String category);

    /** L0 确认 token 查工单。 */
    Optional<Ticket> findByL0ConfirmToken(String l0ConfirmToken);

    /** 全部进入过 L0 流程的工单（L0 报表）。 */
    List<Ticket> findByL0StateIsNotNull();
}
