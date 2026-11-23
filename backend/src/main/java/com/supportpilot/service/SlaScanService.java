package com.supportpilot.service;

import com.supportpilot.common.DisplayUtil;
import com.supportpilot.common.NotifyType;
import com.supportpilot.common.audit.AuditContext;
import com.supportpilot.entity.Ticket;
import com.supportpilot.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * SLA 定时扫描（V2.1 M2）：
 * - 每 5 分钟评估计时中工单；
 * - 达到预警阈值发一次预警，违约发一次超时；
 * - 违约满 N 分钟（默认 30）自动升级，均以时间戳字段保证幂等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlaScanService {

    private final TicketRepository ticketRepository;
    private final SlaService slaService;
    private final SlaPolicyService slaPolicyService;
    private final NotificationDispatcher notificationDispatcher;
    private final OperationLogService operationLogService;

    @Value("${app.sla.auto-escalate.enabled:true}")
    private boolean autoEscalateEnabled;

    @Value("${app.sla.auto-escalate.delay-minutes:30}")
    private long autoEscalateDelayMinutes;

    @Scheduled(cron = "${app.sla.scan.cron:0 */5 * * * ?}")
    @Transactional
    public void scan() {
        List<String> states = new ArrayList<>(List.of(
                SlaPolicyService.STATE_RUNNING, SlaPolicyService.STATE_WARNING));
        if (autoEscalateEnabled) {
            states.add(SlaPolicyService.STATE_BREACHED);
        }
        List<Ticket> candidates = ticketRepository.findSlaCandidates(states);
        if (candidates.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        int warning = 0;
        int breach = 0;
        int escalated = 0;
        for (Ticket t : candidates) {
            if (t.getCreatedAt() == null) {
                continue;
            }
            SlaService.SlaEvaluation e = slaService.evaluate(t, now);
            if (SlaService.BREACHED.equals(e.state())) {
                if (t.getSlaBreachedAt() == null) {
                    t.setSlaBreachedAt(now);
                    t.setSlaState(SlaPolicyService.STATE_BREACHED);
                    ticketRepository.save(t);
                    notificationDispatcher.dispatchSlaEvent(NotifyType.SLA_BREACH, t, "已超时");
                    breach++;
                }
                if (autoEscalate(t, now)) {
                    escalated++;
                }
            } else if (SlaService.WARNING.equals(e.state())) {
                if (t.getSlaWarningSentAt() == null) {
                    t.setSlaWarningSentAt(now);
                    t.setSlaState(SlaPolicyService.STATE_WARNING);
                    ticketRepository.save(t);
                    notificationDispatcher.dispatchSlaEvent(NotifyType.SLA_WARNING, t,
                            formatRemaining(e.remainingSeconds()));
                    warning++;
                }
            }
        }
        if (warning + breach + escalated > 0) {
            log.info("[SLA 扫描] 候选 {} 条：预警 {}，超时 {}，自动升级 {}",
                    candidates.size(), warning, breach, escalated);
        }
    }

    /** 违约满配置分钟数后自动升级，已升级工单不在候选内。 */
    private boolean autoEscalate(Ticket t, Instant now) {
        if (!autoEscalateEnabled || t.getSlaBreachedAt() == null) {
            return false;
        }
        if (now.isBefore(t.getSlaBreachedAt().plusSeconds(autoEscalateDelayMinutes * 60L))) {
            return false;
        }
        String oldStatus = t.getStatus();
        t.setStatus("已升级");
        t.setStatusClass(DisplayUtil.statusClass("已升级"));
        t.setUpdateTime("刚刚");
        slaService.onStatusChange(t, oldStatus, "已升级");
        slaPolicyService.markStopped(t);
        ticketRepository.save(t);
        audit(t);
        log.warn("[SLA 扫描] 工单 #{} 违约超过 {} 分钟，已自动升级", t.getId(), autoEscalateDelayMinutes);
        return true;
    }

    private void audit(Ticket t) {
        try {
            AuditContext ctx = new AuditContext(null, "系统", "system",
                    t.getTenant() == null ? null : t.getTenant().getId(),
                    "sla", "auto_escalate", "ticket", String.valueOf(t.getId()),
                    "SLA 违约超过 " + autoEscalateDelayMinutes + " 分钟，自动升级");
            operationLogService.record(ctx, null, null, null, true, null, 0);
        } catch (Exception ignored) {
            // 审计失败不影响扫描
        }
    }

    private String formatRemaining(long sec) {
        long min = Math.max(0L, sec) / 60;
        if (min < 60) {
            return "剩余 " + min + " 分钟";
        }
        return "剩余 " + (min / 60) + " 小时 " + (min % 60) + " 分";
    }
}
