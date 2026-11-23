package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.common.NotifyType;
import com.supportpilot.common.TenantContext;
import com.supportpilot.common.UserContext;
import com.supportpilot.common.audit.AuditContext;
import com.supportpilot.entity.SentimentResult;
import com.supportpilot.entity.Ticket;
import com.supportpilot.entity.TicketMessage;
import com.supportpilot.repository.SentimentResultRepository;
import com.supportpilot.repository.TicketMessageRepository;
import com.supportpilot.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * M5 客户情绪预警：词典法分析消息情绪，负面高风险时提醒处理人与主管，
 * 支持一键升级优先级并输出客户情绪趋势。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentService {

    private static final Set<String> POSITIVE = Set.of(
            "谢谢", "感谢", "满意", "很好", "不错", "棒", "赞", "辛苦", "快速", "及时",
            "解决", "专业", "耐心", "给力", "好用");
    private static final Set<String> NEGATIVE = Set.of(
            "差", "垃圾", "失望", "愤怒", "生气", "投诉", "退款", "退费", "赔偿", "太慢",
            "崩溃", "故障", "无法", "不能用", "着急", "骗", "恶心", "糟糕", "不行", "没用",
            "报错", "卡死", "宕机", "严重", "紧急", "催");
    private static final Set<String> INTENSIFIERS = Set.of(
            "非常", "特别", "太", "极其", "十分", "很", "真", "超级", "极度");

    private final SentimentResultRepository sentimentRepository;
    private final TicketMessageRepository ticketMessageRepository;
    private final TicketRepository ticketRepository;
    private final SmartAlertService smartAlertService;
    private final OperationLogService operationLogService;

    /** 分析并落库一条消息的情绪；异常由调用方捕获。 */
    @Transactional
    public void analyzeAndRecord(Ticket ticket, TicketMessage message, String text) {
        Analysis a = analyze(text);
        message.setSentiment(a.sentiment);
        message.setSentimentIntensity(a.intensity);
        if (message.getId() != null) {
            ticketMessageRepository.save(message);
        }
        SentimentResult r = new SentimentResult();
        r.setTicketId(ticket.getId());
        r.setMessageId(message.getId());
        r.setSentiment(a.sentiment);
        r.setIntensity(a.intensity);
        r.setKeywords(a.keywords);
        r.setCreatedAt(Instant.now());
        sentimentRepository.save(r);

        if (!SentimentResult.NEGATIVE.equals(a.sentiment)) {
            return;
        }
        long negatives = sentimentRepository.countByTicketIdAndSentiment(
                ticket.getId(), SentimentResult.NEGATIVE);
        boolean highRisk = a.intensity >= 0.8 || negatives >= 2;
        if (!highRisk) {
            return;
        }
        Long tenantId = ticket.getTenant() == null ? null : ticket.getTenant().getId();
        String title = "【负面情绪】#" + ticket.getId() + " " + nullSafe(ticket.getTitle());
        String content = "客户情绪偏负面（强度 " + a.intensity + "），关键词：" + a.keywords
                + "。建议尽快安抚并优先处理。";
        if (ticket.getAssigneeId() != null) {
            smartAlertService.notifyUsers(List.of(ticket.getAssigneeId()), tenantId,
                    NotifyType.SENTIMENT_ALERT, title, content,
                    "ticket", String.valueOf(ticket.getId()));
        }
        smartAlertService.notifyManagers(tenantId, NotifyType.SENTIMENT_ALERT, title, content,
                "ticket", String.valueOf(ticket.getId()));
        log.info("[情绪预警] 工单 #{} 负面情绪触发告警（强度 {}，累计 {} 条）",
                ticket.getId(), a.intensity, negatives);
    }

    /** 一键升级为 P2（已为 P1/P2 时不降级）。 */
    @Transactional
    public Map<String, Object> upgrade(Long ticketId) {
        Ticket t = ticketRepository.findById(ticketId)
                .orElseThrow(() -> BusinessException.notFound("工单不存在"));
        if ("P1".equals(t.getPriority()) || "P2".equals(t.getPriority())) {
            return Map.of("upgraded", false, "priority", t.getPriority());
        }
        String before = t.getPriority();
        t.setPriority("P2");
        t.setPriorityClass("pri-p2");
        t.setUpdateTime("刚刚");
        ticketRepository.save(t);
        UserContext.CurrentUser me = UserContext.get();
        try {
            AuditContext ctx = new AuditContext(
                    me == null ? null : me.getId(), me == null ? null : me.getName(),
                    me == null ? null : me.getUsername(), TenantContext.get(),
                    "ticket", "sentiment-upgrade", "ticket", String.valueOf(ticketId),
                    "负面情绪升级优先级 " + before + " → P2");
            operationLogService.record(ctx, null, null, null, true, null, 0);
        } catch (Exception ignored) {
            // 审计失败不影响升级结果
        }
        return Map.of("upgraded", true, "priority", "P2");
    }

    /** 近 90 天客户情绪趋势：按天返回负面占比与平均强度。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> trend(Long customerId) {
        Instant since = Instant.now().minusSeconds(90L * 24 * 3600);
        List<Ticket> tickets = ticketRepository.findByCustomerId(customerId);
        List<Long> ids = tickets.stream().map(Ticket::getId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<LocalDate, int[]> counter = new LinkedHashMap<>(); // count / negative
        Map<LocalDate, Double> intensitySum = new LinkedHashMap<>();
        ZoneId zone = ZoneId.systemDefault();
        for (SentimentResult r : sentimentRepository.findByTicketIdIn(ids)) {
            if (r.getCreatedAt() == null || r.getCreatedAt().isBefore(since)) {
                continue;
            }
            LocalDate day = r.getCreatedAt().atZone(zone).toLocalDate();
            int[] v = counter.computeIfAbsent(day, k -> new int[2]);
            v[0]++;
            if (SentimentResult.NEGATIVE.equals(r.getSentiment())) {
                v[1]++;
            }
            intensitySum.merge(day, r.getIntensity() == null ? 0.0 : r.getIntensity(), Double::sum);
        }
        List<Map<String, Object>> list = new ArrayList<>();
        counter.forEach((day, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", day.format(DateTimeFormatter.ISO_LOCAL_DATE));
            m.put("count", v[0]);
            m.put("negativeRatio", v[0] == 0 ? 0 : Math.round((double) v[1] / v[0] * 1000.0) / 1000.0);
            m.put("avgIntensity", Math.round(intensitySum.getOrDefault(day, 0.0) / v[0] * 1000.0) / 1000.0);
            list.add(m);
        });
        list.sort(Comparator.comparing(m -> String.valueOf(m.get("date"))));
        return list;
    }

    // ---------------- 词典分析 ----------------

    private Analysis analyze(String text) {
        Analysis a = new Analysis();
        if (text == null || text.isBlank()) {
            a.sentiment = SentimentResult.NEUTRAL;
            a.intensity = 0;
            a.keywords = "";
            return a;
        }
        String t = text.toLowerCase(Locale.ROOT);
        List<String> negHits = new ArrayList<>();
        List<String> posHits = new ArrayList<>();
        NEGATIVE.forEach(w -> {
            if (t.contains(w)) {
                negHits.add(w);
            }
        });
        POSITIVE.forEach(w -> {
            if (t.contains(w)) {
                posHits.add(w);
            }
        });
        int boost = 0;
        for (String w : INTENSIFIERS) {
            if (t.contains(w)) {
                boost++;
            }
        }
        int neg = negHits.size();
        int pos = posHits.size();
        if (neg == 0 && pos == 0) {
            a.sentiment = SentimentResult.NEUTRAL;
            a.intensity = 0;
            a.keywords = "";
            return a;
        }
        if (neg >= pos) {
            a.sentiment = SentimentResult.NEGATIVE;
            a.intensity = clamp(0.5 + 0.15 * (neg - 1) + 0.1 * boost);
            a.keywords = String.join(",", negHits.subList(0, Math.min(5, negHits.size())));
        } else {
            a.sentiment = SentimentResult.POSITIVE;
            a.intensity = clamp(0.4 + 0.15 * (pos - 1) + 0.05 * boost);
            a.keywords = String.join(",", posHits.subList(0, Math.min(5, posHits.size())));
        }
        return a;
    }

    private static double clamp(double v) {
        double c = Math.max(0, Math.min(1, v));
        return Math.round(c * 100.0) / 100.0;
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static final class Analysis {
        private String sentiment;
        private double intensity;
        private String keywords;
    }
}
