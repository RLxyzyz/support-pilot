package com.supportpilot.service;

import com.supportpilot.dto.TicketVO;
import com.supportpilot.entity.Ticket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * SLA 实时计时（V2.1）：
 * - 优先使用工单创建时的快照（到期时间 / 日历类型 / 预警阈值）；
 * - WORKTIME 只累计工作时段，NATURAL 按自然时间；
 * - 「待客户」期间冻结，已解决 / 已升级停止计时；
 * - 返回 正常 / 预警 / 超时 / 暂停 / 停止 五态与剩余时间。
 */
@Service
public class SlaService {

    public static final String NORMAL = "NORMAL";
    public static final String WARNING = "WARNING";
    public static final String BREACHED = "BREACHED";
    public static final String PAUSED = "PAUSED";
    public static final String STOPPED = "STOPPED";
    public static final String MET = "MET";

    @Value("${app.sla.p1-hours:4}")
    private int p1Hours;
    @Value("${app.sla.p2-hours:8}")
    private int p2Hours;
    @Value("${app.sla.p3-hours:24}")
    private int p3Hours;
    @Value("${app.sla.p4-hours:48}")
    private int p4Hours;

    private final SlaCalendarService calendarService;

    public SlaService(SlaCalendarService calendarService) {
        this.calendarService = calendarService;
    }

    /** 一次 SLA 评估结果；扫描任务与 VO 展示共用同一套口径。 */
    public record SlaEvaluation(String state, int pct, int threshold,
                                long consumedSeconds, long totalSeconds,
                                long remainingSeconds, String display, String color) {
    }

    public void fillSla(TicketVO vo, Ticket t) {
        if (vo == null || t == null) {
            return;
        }
        SlaEvaluation e = evaluate(t, Instant.now());
        vo.setSla(e.display());
        vo.setSlaPct(e.pct());
        vo.setSlaColor(e.color());
        vo.setSlaState(e.state());
        vo.setSlaResolveDueAt(formatTime(t.getResolveDueAt()));
        vo.setSlaFirstResponseDueAt(formatTime(t.getFirstResponseDueAt()));
        vo.setSlaCalendarType(t.getSlaCalendarType() == null ? "NATURAL" : t.getSlaCalendarType());
        vo.setSlaSource(t.getSlaPolicyId() == null ? "GLOBAL" : "POLICY");
    }

    public SlaEvaluation evaluate(Ticket t, Instant now) {
        if (t == null) {
            return new SlaEvaluation(STOPPED, 0, 80, 0, 0, 0, "—", "#94a3b8");
        }
        String status = t.getStatus();
        boolean closed = "已解决".equals(status) || "已升级".equals(status);
        int threshold = t.getSlaWarningThreshold() == null ? 80 : t.getSlaWarningThreshold();

        if (t.getCreatedAt() == null) {
            return new SlaEvaluation(closed ? STOPPED : NORMAL, 0, threshold, 0, 0, 0, "—", "#94a3b8");
        }

        long totalSec = totalSeconds(t);
        long pausedSec = t.getSlaPausedSeconds() == null ? 0L : t.getSlaPausedSeconds();
        if ("待客户".equals(status) && t.getSlaPausedAt() != null) {
            pausedSec += Math.max(0L, Duration.between(t.getSlaPausedAt(), now).getSeconds());
        }
        Instant effectiveNow = closed && t.getResolvedAt() != null ? t.getResolvedAt() : now;
        long consumed = consumedSeconds(t, effectiveNow) - pausedSec;
        consumed = Math.max(0L, Math.min(consumed, totalSec));
        int pct = (int) Math.min(100, Math.round(consumed * 100.0 / Math.max(totalSec, 1L)));
        long remaining = Math.max(0L, totalSec - consumed);

        if (closed) {
            if (SlaPolicyService.STATE_BREACHED.equals(t.getSlaState())
                    || (t.getResolvedAt() != null && t.getResolveDueAt() != null
                    && t.getResolvedAt().isAfter(t.getResolveDueAt().plusSeconds(pausedSec)))) {
                return new SlaEvaluation(BREACHED, 100, threshold, consumed, totalSec, 0, "已超时", "#dc2626");
            }
            if ("已解决".equals(status) && MET.equals(t.getSlaState())) {
                return new SlaEvaluation(MET, pct, threshold, consumed, totalSec, remaining, "已达成", "#059669");
            }
            return new SlaEvaluation(STOPPED, pct, threshold, consumed, totalSec, remaining, "已停止计时", "#cbd5e1");
        }

        if ("待客户".equals(status)) {
            return new SlaEvaluation(PAUSED, pct, threshold, consumed, totalSec, remaining,
                    "计时暂停（待客户）", "#64748b");
        }
        if (remaining <= 0) {
            return new SlaEvaluation(BREACHED, 100, threshold, consumed, totalSec, 0, "已超时", "#dc2626");
        }
        if (pct >= threshold) {
            return new SlaEvaluation(WARNING, pct, threshold, consumed, totalSec, remaining,
                    formatRemaining(remaining), "#d97706");
        }
        return new SlaEvaluation(NORMAL, pct, threshold, consumed, totalSec, remaining,
                formatRemaining(remaining), pct < 50 ? "#059669" : "#d97706");
    }

    private long totalSeconds(Ticket t) {
        if (t.getResolveDueAt() != null) {
            long sec = calendarService.consumedSeconds(t.getCreatedAt(), t.getResolveDueAt(),
                    t.getSlaCalendarType(), calendarId(t), tenantId(t));
            if (sec > 0) {
                return sec;
            }
        }
        return (long) limitHoursFor(t.getPriority()) * 3600L;
    }

    private long consumedSeconds(Ticket t, Instant until) {
        if (t.getResolveDueAt() != null) {
            return calendarService.consumedSeconds(t.getCreatedAt(), until,
                    t.getSlaCalendarType(), calendarId(t), tenantId(t));
        }
        return Math.max(0L, Duration.between(t.getCreatedAt(), until).getSeconds());
    }

    private Long calendarId(Ticket t) {
        return t.getSlaCalendarId();
    }

    private Long tenantId(Ticket t) {
        return t.getTenant() == null ? null : t.getTenant().getId();
    }

    /**
     * 状态变更时维护暂停计时段：进入「待客户」记录起点；离开时累计秒数并清空。
     */
    public void onStatusChange(Ticket t, String oldStatus, String newStatus) {
        if (oldStatus == null || oldStatus.equals(newStatus)) {
            return;
        }
        boolean entering = "待客户".equals(newStatus);
        boolean leaving = "待客户".equals(oldStatus);
        if (entering && !leaving) {
            t.setSlaPausedAt(Instant.now());
        } else if (leaving && !entering) {
            Instant pausedAt = t.getSlaPausedAt();
            if (pausedAt != null) {
                long paused = Math.max(0L, Duration.between(pausedAt, Instant.now()).getSeconds());
                long acc = t.getSlaPausedSeconds() == null ? 0L : t.getSlaPausedSeconds();
                t.setSlaPausedSeconds(acc + paused);
            }
            t.setSlaPausedAt(null);
        }
    }

    /** 指标口径：已解决工单是否在快照时限内达成（不含暂停时间）。 */
    public boolean isMet(Ticket t) {
        if (t.getResolvedAt() == null || t.getCreatedAt() == null) {
            return false;
        }
        long paused = t.getSlaPausedSeconds() == null ? 0L : t.getSlaPausedSeconds();
        if (t.getResolveDueAt() != null) {
            return !t.getResolvedAt().isAfter(t.getResolveDueAt().plusSeconds(paused));
        }
        long elapsed = Math.max(0L, Duration.between(t.getCreatedAt(), t.getResolvedAt()).getSeconds() - paused);
        return elapsed <= (long) limitHoursFor(t.getPriority()) * 3600L;
    }

    public int limitHoursFor(String priority) {
        return limitFor(priority);
    }

    public int limitFor(String priority) {
        if (priority == null) {
            return p4Hours;
        }
        return switch (priority) {
            case "P1" -> p1Hours;
            case "P2" -> p2Hours;
            case "P3" -> p3Hours;
            default -> p4Hours;
        };
    }

    private static final java.time.format.DateTimeFormatter TIME_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(java.time.ZoneId.of("Asia/Shanghai"));

    private String formatTime(Instant instant) {
        return instant == null ? null : TIME_FORMAT.format(instant);
    }

    private String formatRemaining(long totalSec) {
        long totalMin = Math.max(0L, totalSec) / 60;
        if (totalMin < 60) {
            return "剩余 " + totalMin + " 分钟";
        }
        long h = totalMin / 60;
        long m = totalMin % 60;
        return m == 0 ? "剩余 " + h + " 小时" : "剩余 " + h + " 小时 " + m + " 分";
    }
}
