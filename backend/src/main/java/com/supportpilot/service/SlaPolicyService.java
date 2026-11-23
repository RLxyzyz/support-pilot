package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.common.TenantContext;
import com.supportpilot.common.UserContext;
import com.supportpilot.common.audit.AuditContext;
import com.supportpilot.entity.Customer;
import com.supportpilot.entity.SlaPolicy;
import com.supportpilot.entity.Ticket;
import com.supportpilot.repository.CustomerRepository;
import com.supportpilot.repository.SlaPolicyRepository;
import com.supportpilot.repository.TicketRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * SLA 策略（V2.1 M1）：
 * - 解析：精确「等级×优先级」→ 默认等级 → yml 全局回落；
 * - 创建工单时写入不可变快照（到期时间、日历类型、预警阈值）；
 * - 策略变更后重算未解决工单 / 存量补算。
 */
@Slf4j
@Service
public class SlaPolicyService {

    public static final String STATE_RUNNING = "RUNNING";
    public static final String STATE_WARNING = "WARNING";
    public static final String STATE_BREACHED = "BREACHED";
    public static final String STATE_MET = "MET";
    public static final String STATE_STOPPED = "STOPPED";

    private static final String DEFAULT_TIER = "默认";
    private static final List<String> TIERS = List.of("金牌", "银牌", "铜牌", "默认");
    private static final List<String> PRIORITIES = List.of("P1", "P2", "P3", "P4");

    private final SlaPolicyRepository policyRepository;
    private final CustomerRepository customerRepository;
    private final TicketRepository ticketRepository;
    private final SlaCalendarService calendarService;
    private final OperationLogService operationLogService;

    @Value("${app.sla.p1-hours:4}")
    private long p1Hours;
    @Value("${app.sla.p2-hours:8}")
    private long p2Hours;
    @Value("${app.sla.p3-hours:24}")
    private long p3Hours;
    @Value("${app.sla.p4-hours:48}")
    private long p4Hours;
    @Value("${app.sla.frt-p1-minutes:30}")
    private int frtP1;
    @Value("${app.sla.frt-p2-minutes:60}")
    private int frtP2;
    @Value("${app.sla.frt-p3-minutes:240}")
    private int frtP3;
    @Value("${app.sla.frt-p4-minutes:480}")
    private int frtP4;

    public SlaPolicyService(SlaPolicyRepository policyRepository,
                            CustomerRepository customerRepository,
                            TicketRepository ticketRepository,
                            SlaCalendarService calendarService,
                            OperationLogService operationLogService) {
        this.policyRepository = policyRepository;
        this.customerRepository = customerRepository;
        this.ticketRepository = ticketRepository;
        this.calendarService = calendarService;
        this.operationLogService = operationLogService;
    }

    /** 解析结果；policyId 为 null 表示走 yml 全局回落。 */
    public record ResolvedPolicy(Long policyId, String tier, String priority,
                                 int firstResponseMinutes, int resolveMinutes,
                                 String calendarType, Long calendarId,
                                 int warningThreshold, String source) {
    }

    public record RecalcResult(int total, int updated, String reason) {
    }

    public ResolvedPolicy resolve(Long tenantId, String tier, String priority) {
        long tenantKey = tenantKey(tenantId);
        String safeTier = TIERS.contains(tier) ? tier : DEFAULT_TIER;
        String safePriority = PRIORITIES.contains(priority) ? priority : "P3";

        SlaPolicy exact = policyRepository
                .findFirstByTenantIdAndTierAndPriorityAndEnabledTrue(tenantKey, safeTier, safePriority)
                .orElse(null);
        if (exact != null) {
            return toResolved(exact, "TIER");
        }
        if (!DEFAULT_TIER.equals(safeTier)) {
            SlaPolicy fallback = policyRepository
                    .findFirstByTenantIdAndTierAndPriorityAndEnabledTrue(tenantKey, DEFAULT_TIER, safePriority)
                    .orElse(null);
            if (fallback != null) {
                return toResolved(fallback, "DEFAULT_TIER");
            }
        }
        return new ResolvedPolicy(null, safeTier, safePriority,
                frtMinutes(safePriority), (int) (resolveHours(safePriority) * 60),
                "NATURAL", null, 80, "GLOBAL");
    }

    private ResolvedPolicy toResolved(SlaPolicy p, String source) {
        return new ResolvedPolicy(p.getId(), p.getTier(), p.getPriority(),
                p.getFirstResponseMinutes(), p.getResolveMinutes(),
                p.getCalendarType(), p.getCalendarId(), p.getWarningThreshold(), source);
    }

    /** 创建工单时写入 SLA 快照（FR-S02）。 */
    public void applySnapshot(Ticket ticket) {
        if (ticket == null || ticket.getCreatedAt() == null) {
            return;
        }
        Long tid = ticket.getTenant() == null ? TenantContext.get() : ticket.getTenant().getId();
        ResolvedPolicy p = resolve(tid, tierOf(ticket.getCustomerId()), ticket.getPriority());
        Instant base = ticket.getCreatedAt();
        ticket.setSlaPolicyId(p.policyId());
        ticket.setSlaCalendarType(p.calendarType());
        ticket.setSlaCalendarId(p.calendarId());
        ticket.setSlaWarningThreshold(p.warningThreshold());
        ticket.setFirstResponseDueAt(calendarService.addMinutes(
                base, p.firstResponseMinutes(), p.calendarType(), p.calendarId(), tid));
        ticket.setResolveDueAt(calendarService.addMinutes(
                base, p.resolveMinutes(), p.calendarType(), p.calendarId(), tid));
        ticket.setSlaState(STATE_RUNNING);
        ticket.setSlaWarningSentAt(null);
        ticket.setSlaBreachedAt(null);
    }

    /** 从「已解决」重开：按当前策略重新快照并重置预警/违约标记（FR-S09）。 */
    public void resetOnReopen(Ticket ticket) {
        applySnapshot(ticket);
    }

    /** 解决时标记达成 / 违约（FR-S09 / S10）。 */
    public void markResolved(Ticket ticket) {
        if (STATE_BREACHED.equals(ticket.getSlaState())) {
            return;
        }
        ticket.setSlaState(STATE_MET);
    }

    /** 升级 / 终止时停止计时（FR-S10）。 */
    public void markStopped(Ticket ticket) {
        if (!STATE_MET.equals(ticket.getSlaState()) && !STATE_BREACHED.equals(ticket.getSlaState())) {
            ticket.setSlaState(STATE_STOPPED);
        }
    }

    public String tierOf(Long customerId) {
        if (customerId == null) {
            return DEFAULT_TIER;
        }
        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null || customer.getSlaTier() == null || customer.getSlaTier().isBlank()) {
            return DEFAULT_TIER;
        }
        return customer.getSlaTier();
    }

    // ---------------- 策略维护 ----------------

    public List<SlaPolicy> list() {
        return policyRepository.findByTenantIdOrderByTierAscPriorityAsc(tenantKey(TenantContext.get())).stream()
                .sorted(Comparator.comparing(SlaPolicy::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public List<SlaPolicy> listAllTiers() {
        List<SlaPolicy> all = new ArrayList<>(list());
        all.sort((a, b) -> {
            int t = Integer.compare(TIERS.indexOf(a.getTier()), TIERS.indexOf(b.getTier()));
            return t != 0 ? t : Integer.compare(PRIORITIES.indexOf(a.getPriority()), PRIORITIES.indexOf(b.getPriority()));
        });
        return all;
    }

    @Transactional
    public SlaPolicy upsert(String tier, String priority, Integer firstResponseMinutes,
                            Integer resolveMinutes, String calendarType, Long calendarId,
                            Integer warningThreshold, Boolean enabled) {
        if (!TIERS.contains(tier)) {
            throw new BusinessException(400, "服务等级必须是 金牌/银牌/铜牌/默认");
        }
        if (!PRIORITIES.contains(priority)) {
            throw new BusinessException(400, "优先级必须是 P1/P2/P3/P4");
        }
        validateMinutes(firstResponseMinutes, "首次响应时限");
        validateMinutes(resolveMinutes, "解决时限");
        String type = "WORKTIME".equalsIgnoreCase(calendarType) ? "WORKTIME" : "NATURAL";
        if ("WORKTIME".equals(type) && calendarId == null) {
            throw new BusinessException(400, "工作日历模式必须选择日历");
        }
        int threshold = warningThreshold == null ? 80 : warningThreshold;
        if (threshold < 50 || threshold > 95) {
            throw new BusinessException(400, "预警阈值必须在 50–95 之间");
        }
        long tenantKey = tenantKey(TenantContext.get());
        SlaPolicy policy = policyRepository
                .findFirstByTenantIdAndTierAndPriority(tenantKey, tier, priority)
                .orElseGet(SlaPolicy::new);
        if (policy.getId() == null) {
            policy.setTenantId(tenantKey);
            policy.setTier(tier);
            policy.setPriority(priority);
            policy.setCreatedAt(Instant.now());
        }
        policy.setFirstResponseMinutes(firstResponseMinutes);
        policy.setResolveMinutes(resolveMinutes);
        policy.setCalendarType(type);
        policy.setCalendarId("WORKTIME".equals(type) ? calendarId : null);
        policy.setWarningThreshold(threshold);
        policy.setEnabled(enabled == null || enabled);
        policy.setUpdatedAt(Instant.now());
        return policyRepository.save(policy);
    }

    /** 对未解决工单重算快照（FR-S05 手动重算）。 */
    @Transactional
    public RecalcResult recalculateAll(String reason) {
        long tenantKey = tenantKey(TenantContext.get());
        List<Ticket> tickets = ticketRepository.findByStatusNotIn(List.of("已解决", "已升级"));
        int updated = 0;
        for (Ticket t : tickets) {
            if (t.getCreatedAt() == null) {
                continue;
            }
            Long tid = t.getTenant() == null ? tenantKey : t.getTenant().getId();
            if (tid != null && tenantKey != 0L && !tid.equals(tenantKey)) {
                continue;
            }
            applySnapshot(t);
            updated++;
        }
        ticketRepository.saveAll(tickets);
        audit("sla_recalculate", "重算未解决工单 " + updated + " 条，原因：" + reason);
        return new RecalcResult(tickets.size(), updated, reason);
    }

    /** 存量补算：仅处理尚无到期快照的工单（FR-S05），可重复执行。 */
    @Transactional
    public int backfill() {
        int total = 0;
        while (true) {
            List<Ticket> batch = ticketRepository.findBackfillCandidates(PageRequest.of(0, 500));
            if (batch.isEmpty()) {
                break;
            }
            for (Ticket t : batch) {
                applySnapshot(t);
            }
            ticketRepository.saveAll(batch);
            total += batch.size();
            if (total >= 50_000) {
                log.warn("SLA 存量补算达到 50000 条上限，请再次执行");
                break;
            }
        }
        audit("sla_backfill", "存量工单补算 " + total + " 条");
        return total;
    }

    /** 模拟计算（FR-S08）：按指定等级×优先级返回解决时限、到期时间与当前消耗。 */
    public java.util.Map<String, Object> simulate(String tier, String priority, Instant start, Instant now) {
        Long tid = TenantContext.get();
        ResolvedPolicy p = resolve(tid, tier, priority);
        Instant base = start == null ? Instant.now() : start;
        Instant at = now == null ? Instant.now() : now;
        Instant due = calendarService.addMinutes(base, p.resolveMinutes(), p.calendarType(), p.calendarId(), tid);
        long total = calendarService.consumedSeconds(base, due, p.calendarType(), p.calendarId(), tid);
        long consumed = calendarService.consumedSeconds(base, at, p.calendarType(), p.calendarId(), tid);
        int pct = (int) Math.min(100, Math.round(consumed * 100.0 / Math.max(total, 1L)));
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("tier", p.tier());
        result.put("priority", p.priority());
        result.put("source", p.source());
        result.put("policyId", p.policyId());
        result.put("firstResponseMinutes", p.firstResponseMinutes());
        result.put("resolveMinutes", p.resolveMinutes());
        result.put("calendarType", p.calendarType());
        result.put("calendarId", p.calendarId());
        result.put("warningThreshold", p.warningThreshold());
        result.put("startAt", base.toString());
        result.put("resolveDueAt", due == null ? null : due.toString());
        result.put("consumedSeconds", consumed);
        result.put("totalSeconds", total);
        result.put("percent", pct);
        result.put("breached", consumed >= total);
        return result;
    }

    private void validateMinutes(Integer value, String label) {
        if (value == null || value < 1 || value > 10080) {
            throw new BusinessException(400, label + "必须在 1–10080 分钟之间");
        }
    }

    private void audit(String action, String summary) {
        UserContext.CurrentUser u = UserContext.get();
        AuditContext ctx = new AuditContext(
                u == null ? null : u.getId(),
                u == null ? null : u.getName(),
                u == null ? null : u.getUsername(),
                TenantContext.get(), "sla", action, "sla_policy", null, summary);
        operationLogService.record(ctx, null, null, null, true, null, 0);
    }

    public static long tenantKey(Long tenantId) {
        return tenantId == null ? 0L : tenantId;
    }

    private long resolveHours(String priority) {
        return switch (priority) {
            case "P1" -> p1Hours;
            case "P2" -> p2Hours;
            case "P3" -> p3Hours;
            default -> p4Hours;
        };
    }

    private int frtMinutes(String priority) {
        return switch (priority) {
            case "P1" -> frtP1;
            case "P2" -> frtP2;
            case "P3" -> frtP3;
            default -> frtP4;
        };
    }
}
