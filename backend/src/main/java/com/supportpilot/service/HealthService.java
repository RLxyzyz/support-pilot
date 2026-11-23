package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.common.NotifyType;
import com.supportpilot.common.TenantContext;
import com.supportpilot.entity.Customer;
import com.supportpilot.entity.HealthScoreConfig;
import com.supportpilot.entity.HealthScoreDaily;
import com.supportpilot.entity.SentimentResult;
import com.supportpilot.entity.Ticket;
import com.supportpilot.repository.CustomerRepository;
import com.supportpilot.repository.HealthScoreConfigRepository;
import com.supportpilot.repository.HealthScoreDailyRepository;
import com.supportpilot.repository.SentimentResultRepository;
import com.supportpilot.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * M7 客户健康分：综合工单频率、SLA 违约、CSAT 与负面情绪计算 0-100 分，
 * 每日 02:00 计算并落快照，低于 60 或周环比骤降时告警。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthService {

    private final CustomerRepository customerRepository;
    private final TicketRepository ticketRepository;
    private final SentimentResultRepository sentimentRepository;
    private final HealthScoreDailyRepository dailyRepository;
    private final HealthScoreConfigRepository configRepository;
    private final SmartAlertService smartAlertService;

    @Value("${app.tenant.default-id:1}")
    private Long defaultTenantId;

    /** 每日 02:00 计算活跃客户健康分（FR-M7-01）。 */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void computeDaily() {
        int count = 0;
        for (Customer c : customerRepository.findAll()) {
            if ("disabled".equalsIgnoreCase(c.getStatus())) {
                continue;
            }
            try {
                computeForCustomer(c, LocalDate.now());
                count++;
            } catch (Exception e) {
                log.warn("[健康分] 客户 {} 计算失败：{}", c.getId(), e.getMessage());
            }
        }
        log.info("[健康分] 完成 {} 个客户的计算", count);
    }

    /** GET /customers/{id}/health。 */
    @Transactional
    public Map<String, Object> health(Long customerId) {
        Customer c = customerRepository.findById(customerId)
                .orElseThrow(() -> BusinessException.notFound("客户不存在"));
        HealthScoreDaily latest = dailyRepository.findFirstByCustomerIdOrderByScoreDateDesc(customerId)
                .orElseGet(() -> computeForCustomer(c, LocalDate.now()));
        LocalDate from = LocalDate.now().minusDays(89);
        List<HealthScoreDaily> trend =
                dailyRepository.findByCustomerIdAndScoreDateBetweenOrderByScoreDateAsc(
                        customerId, from, LocalDate.now());
        List<Map<String, Object>> points = new ArrayList<>();
        for (HealthScoreDaily d : trend) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("date", d.getScoreDate() == null ? null : d.getScoreDate().toString());
            p.put("score", d.getScore());
            points.add(p);
        }
        Map<String, Object> components = new LinkedHashMap<>();
        components.put("freq", latest.getFreqScore());
        components.put("sla", latest.getSlaScore());
        components.put("csat", latest.getCsatScore());
        components.put("sentiment", latest.getSentimentScore());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerId", customerId);
        result.put("customerName", c.getName());
        result.put("score", latest.getScore());
        result.put("level", level(latest.getScore()));
        result.put("components", components);
        result.put("trend", points);
        return result;
    }

    // ---------------- 计算 ----------------

    @Transactional
    public HealthScoreDaily computeForCustomer(Customer c, LocalDate date) {
        HealthScoreConfig cfg = config();
        Instant now = Instant.now();
        Instant d30 = now.minus(30, ChronoUnit.DAYS);
        Instant d90 = now.minus(90, ChronoUnit.DAYS);

        List<Ticket> tickets = ticketRepository.findByCustomerId(c.getId());

        // 1) 工单频率：近 30 天新建数，每单 3 分，封顶权重
        int recent = 0;
        for (Ticket t : tickets) {
            if (t.getCreatedAt() != null && !t.getCreatedAt().isBefore(d30)) {
                recent++;
            }
        }
        double freq = Math.min(cfg.getFreqWeight(), recent * (cfg.getFreqWeight() / 10.0));

        // 2) SLA 违约：近 30 天已解决单中超出解决时限的比例
        int resolved = 0;
        int breached = 0;
        for (Ticket t : tickets) {
            if (t.getResolvedAt() == null || t.getResolvedAt().isBefore(d30) || t.getResolveDueAt() == null) {
                continue;
            }
            resolved++;
            if (t.getResolvedAt().isAfter(t.getResolveDueAt())) {
                breached++;
            }
        }
        double sla = resolved == 0 ? 0 : (double) breached / resolved * cfg.getSlaWeight();

        // 3) CSAT：近 90 天评分均值，无数据不扣分
        double csatSum = 0;
        int csatCount = 0;
        for (Ticket t : tickets) {
            if (t.getCsatScore() == null || (t.getCsatAt() != null && t.getCsatAt().isBefore(d90))) {
                continue;
            }
            csatSum += t.getCsatScore();
            csatCount++;
        }
        double csat = csatCount == 0 ? 0
                : Math.max(0, (5.0 - csatSum / csatCount) / 4.0 * cfg.getCsatWeight());

        // 4) 负面情绪占比（近 30 天），无数据不扣分
        List<Long> ids = tickets.stream().map(Ticket::getId).toList();
        int sentiments = 0;
        int negatives = 0;
        if (!ids.isEmpty()) {
            for (SentimentResult r : sentimentRepository.findByTicketIdIn(ids)) {
                if (r.getCreatedAt() == null || r.getCreatedAt().isBefore(d30)) {
                    continue;
                }
                sentiments++;
                if (SentimentResult.NEGATIVE.equals(r.getSentiment())) {
                    negatives++;
                }
            }
        }
        double sentiment = sentiments == 0 ? 0
                : (double) negatives / sentiments * cfg.getSentimentWeight();

        double score = clamp(100 - freq - sla - csat - sentiment);

        HealthScoreDaily daily = dailyRepository.findByCustomerIdAndScoreDate(c.getId(), date)
                .orElseGet(HealthScoreDaily::new);
        daily.setCustomerId(c.getId());
        daily.setScoreDate(date);
        daily.setScore(round(score));
        daily.setFreqScore(round(freq));
        daily.setSlaScore(round(sla));
        daily.setCsatScore(round(csat));
        daily.setSentimentScore(round(sentiment));
        daily.setCreatedAt(Instant.now());
        dailyRepository.save(daily);

        maybeAlert(c, daily);
        return daily;
    }

    private void maybeAlert(Customer c, HealthScoreDaily today) {
        boolean low = today.getScore() < 60;
        HealthScoreDaily yesterday = dailyRepository
                .findByCustomerIdAndScoreDate(c.getId(), today.getScoreDate().minusDays(1)).orElse(null);
        HealthScoreDaily weekAgo = dailyRepository
                .findByCustomerIdAndScoreDate(c.getId(), today.getScoreDate().minusDays(7)).orElse(null);
        boolean crossing = low && (yesterday == null || yesterday.getScore() >= 60);
        boolean drop = weekAgo != null && weekAgo.getScore() - today.getScore() >= 15;
        if (!crossing && !drop) {
            return;
        }
        String content = "客户「" + c.getName() + "」健康分 " + today.getScore()
                + (drop ? "，较上周下降 " + round(weekAgo.getScore() - today.getScore()) + " 分" : "，已低于 60 分")
                + "，建议重点关注。";
        smartAlertService.notifyManagers(c.getTenantId(), NotifyType.HEALTH_ALERT,
                "【客户健康】" + c.getName() + " 健康分预警", content,
                "customer", String.valueOf(c.getId()));
        log.warn("[健康分] 客户 {} 触发告警：{}", c.getId(), content);
    }

    // ---------------- 配置 ----------------

    @Transactional(readOnly = true)
    public HealthScoreConfig getConfig() {
        return config();
    }

    @Transactional
    public HealthScoreConfig updateConfig(Integer freqWeight, Integer slaWeight,
                                          Integer csatWeight, Integer sentimentWeight) {
        Long tid = tenantKey();
        HealthScoreConfig cfg = configRepository.findByTenantId(tid).orElseGet(() -> {
            HealthScoreConfig x = new HealthScoreConfig();
            x.setTenantId(tid);
            return x;
        });
        cfg.setFreqWeight(normalize(freqWeight, cfg.getFreqWeight(), 30));
        cfg.setSlaWeight(normalize(slaWeight, cfg.getSlaWeight(), 25));
        cfg.setCsatWeight(normalize(csatWeight, cfg.getCsatWeight(), 25));
        cfg.setSentimentWeight(normalize(sentimentWeight, cfg.getSentimentWeight(), 20));
        return configRepository.save(cfg);
    }

    private int normalize(Integer value, Integer current, int def) {
        if (value == null) {
            return current == null ? def : current;
        }
        if (value < 0 || value > 100) {
            throw BusinessException.badRequest("权重需在 0 - 100 之间");
        }
        return value;
    }

    private HealthScoreConfig config() {
        Long tid = tenantKey();
        HealthScoreConfig cfg = configRepository.findByTenantId(tid).orElseGet(HealthScoreConfig::new);
        if (cfg.getFreqWeight() == null) {
            cfg.setFreqWeight(30);
        }
        if (cfg.getSlaWeight() == null) {
            cfg.setSlaWeight(25);
        }
        if (cfg.getCsatWeight() == null) {
            cfg.setCsatWeight(25);
        }
        if (cfg.getSentimentWeight() == null) {
            cfg.setSentimentWeight(20);
        }
        return cfg;
    }

    private Long tenantKey() {
        Long tid = TenantContext.get();
        return tid == null ? defaultTenantId : tid;
    }

    private static String level(double score) {
        if (score >= 80) {
            return "良好";
        }
        if (score >= 60) {
            return "关注";
        }
        return "风险";
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(100, v));
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
