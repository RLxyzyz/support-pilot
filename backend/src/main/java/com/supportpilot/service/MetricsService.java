package com.supportpilot.service;

import com.supportpilot.common.TenantContext;
import com.supportpilot.dto.BoardVO;
import com.supportpilot.entity.Ticket;
import com.supportpilot.entity.User;
import com.supportpilot.repository.AgentRunRepository;
import com.supportpilot.repository.DraftRepository;
import com.supportpilot.repository.EvalCaseRepository;
import com.supportpilot.repository.TicketRepository;
import com.supportpilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 看板指标服务：基于工单表与草稿表真实统计，
 * 知识盲区（misses）从 eval_case 表读取。
 *
 * <p>KPI 卡片为实时快照（当前待响应 / 处理中 / 已解决 / P1），不受时间范围影响；
 * 趋势与运营指标（FRT / MTTR / SLA 达成率 / 负载 / 分类分布）按选定时间范围统计。
 */
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final EvalCaseRepository evalCaseRepository;
    private final TicketRepository ticketRepository;
    private final DraftRepository draftRepository;
    private final UserRepository userRepository;
    private final SlaService slaService;
    private final AgentRunRepository agentRunRepository;

    /** 采纳一条 AI 草稿估算节省的人工时长（分钟），用于 AI 收益粗略估算。 */
    private static final int AI_SAVED_MINUTES_PER_DRAFT = 5;

    /** AI 成本单价（元 / 千 tokens），来自 application.yml。 */
    @Value("${app.ai.cost-per-1k-tokens:0.008}")
    private double costPer1k;

    public BoardVO overview(Integer days, Instant start, Instant end) {
        Long tid = TenantContext.get();
        int d = (days == null || days <= 0) ? 7 : Math.min(days, 365);
        LocalDate today = LocalDate.now();
        Instant since = (start != null)
                ? start
                : today.minusDays(d - 1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant until = (end != null) ? end : Instant.now();

        BoardVO vo = new BoardVO();
        vo.setKpi(buildKpi(tid));
        vo.setTrend(buildTrend(tid, since, d));
        vo.setAdoption(buildAdoption());
        // 知识盲区按租户隔离（tid 为 null 时返回全局种子），最多展示 10 条
        vo.setMisses(evalCaseRepository.findMisses(tid).stream().limit(10).map(c -> {
            BoardVO.MissVO m = new BoardVO.MissVO();
            m.setQuestion(c.getQuestion());
            m.setCount(c.getCount());
            m.setWeight(c.getWeight());
            return m;
        }).toList());
        vo.setOps(buildOps(tid, since, until));
        return vo;
    }

    /** KPI：基于工单表的实时统计（待响应 / 处理中 / 已解决 / P1 待处理）。 */
    private List<BoardVO.KpiVO> buildKpi(Long tid) {
        long pending = ticketRepository.countByStatus(tid, "待分诊")
                + ticketRepository.countByStatus(tid, "待客户");
        long processing = ticketRepository.countByStatus(tid, "处理中");
        long resolved = ticketRepository.countByStatus(tid, "已解决");
        long p1Open = ticketRepository.countByPriorityAndStatusNot(tid, "P1", "已解决");

        List<BoardVO.KpiVO> list = new ArrayList<>();
        list.add(kpi("待响应工单", String.valueOf(pending), "条", "实时统计", "up"));
        list.add(kpi("处理中工单", String.valueOf(processing), "条", "实时统计", "up"));
        list.add(kpi("已解决工单", String.valueOf(resolved), "条", "实时统计", "up"));
        list.add(kpi("P1 待处理", String.valueOf(p1Open), "条", "实时统计", "up"));
        return list;
    }

    private BoardVO.KpiVO kpi(String label, String value, String unit, String delta, String trend) {
        BoardVO.KpiVO k = new BoardVO.KpiVO();
        k.setLabel(label);
        k.setValue(value);
        k.setUnit(unit);
        k.setDelta(delta);
        k.setTrend(trend);
        return k;
    }

    /** 趋势：since 起到今天每天新建工单数，无数据的天补 0。 */
    private List<BoardVO.TrendVO> buildTrend(Long tid, Instant since, int days) {
        Map<String, Long> dayCount = new HashMap<>();
        for (Object[] row : ticketRepository.countNewPerDay(tid, since)) {
            dayCount.put((String) row[0], ((Number) row[1]).longValue());
        }
        String[] weekDays = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        List<BoardVO.TrendVO> list = new ArrayList<>();
        int n = Math.max(days, 1);
        for (int i = n - 1; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            String key = d.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String label;
            if (i == 0) {
                label = "今天";
            } else if (n <= 7) {
                label = weekDays[d.getDayOfWeek().getValue() % 7];
            } else {
                label = d.format(DateTimeFormatter.ofPattern("MM-dd"));
            }
            list.add(trend(label, dayCount.getOrDefault(key, 0L)));
        }
        return list;
    }

    private BoardVO.TrendVO trend(String day, double value) {
        BoardVO.TrendVO t = new BoardVO.TrendVO();
        t.setDay(day);
        t.setValue(value);
        return t;
    }

    /** 采纳率：基于 draft 表的 adopted / rejected 真实计数。 */
    private BoardVO.AdoptionVO buildAdoption() {
        long adopted = draftRepository.countByStatus("adopted");
        long rejected = draftRepository.countByStatus("rejected");
        long total = adopted + rejected;
        double rate = total > 0 ? Math.round(adopted * 1000.0 / total) / 10.0 : 0;
        double adoptPct = total > 0 ? Math.round(adopted * 1000.0 / total) / 10.0 : 0;
        double rejectPct = total > 0 ? Math.round(rejected * 1000.0 / total) / 10.0 : 0;

        BoardVO.AdoptionVO a = new BoardVO.AdoptionVO();
        a.setRate(rate);
        List<BoardVO.AdoptionItemVO> items = new ArrayList<>();
        items.add(adoptionItem("采纳", adoptPct, "#2563eb"));
        items.add(adoptionItem("驳回", rejectPct, "#e2e8f0"));
        a.setItems(items);
        return a;
    }

    private BoardVO.AdoptionItemVO adoptionItem(String label, double pct, String color) {
        BoardVO.AdoptionItemVO i = new BoardVO.AdoptionItemVO();
        i.setLabel(label);
        i.setPct(pct);
        i.setColor(color);
        return i;
    }

    /** 运营指标：SLA 达成率 / FRT / MTTR / 处理人负载 / 分类分布 / AI 收益。 */
    private BoardVO.OpsVO buildOps(Long tid, Instant since, Instant until) {
        BoardVO.OpsVO ops = new BoardVO.OpsVO();

        List<Ticket> resolved = ticketRepository.findResolvedInRange(tid, since, until);
        List<Ticket> responded = ticketRepository.findRespondedInRange(tid, since, until);
        ops.setResolvedCount(resolved.size());
        ops.setRespondedCount(responded.size());

        // MTTR：平均解决时长（小时）
        double mttrHours = 0;
        int mttrN = 0;
        // SLA 达成率（V2.1）：统一使用创建时快照 + 暂停时间口径
        int slaMet = 0;
        for (Ticket t : resolved) {
            if (t.getCreatedAt() == null || t.getResolvedAt() == null) {
                continue;
            }
            long paused = t.getSlaPausedSeconds() == null ? 0L : t.getSlaPausedSeconds();
            long elapsedSec = Math.max(0L,
                    Duration.between(t.getCreatedAt(), t.getResolvedAt()).getSeconds() - paused);
            mttrHours += elapsedSec / 3600.0;
            mttrN++;
            if (slaService.isMet(t)) {
                slaMet++;
            }
        }
        ops.setMttrHours(mttrN > 0 ? Math.round(mttrHours / mttrN * 10.0) / 10.0 : 0);
        ops.setSlaRate(resolved.isEmpty() ? 0 : Math.round(slaMet * 1000.0 / resolved.size()) / 10.0);

        // FRT：平均首次响应时长（分钟）
        double frtMin = 0;
        int frtN = 0;
        for (Ticket t : responded) {
            if (t.getCreatedAt() == null || t.getFirstResponseAt() == null) {
                continue;
            }
            long sec = Duration.between(t.getCreatedAt(), t.getFirstResponseAt()).getSeconds();
            if (sec < 0) {
                continue;
            }
            frtMin += sec / 60.0;
            frtN++;
        }
        ops.setFrtMinutes(frtN > 0 ? Math.round(frtMin / frtN * 10.0) / 10.0 : 0);

        // AI 节省时长估算：范围内采纳草稿数 × 单条节省分钟
        long adoptedInRange = draftRepository.countByStatusAndCreatedAtBetween("adopted", since, until);
        ops.setAiSavedMinutes(adoptedInRange * AI_SAVED_MINUTES_PER_DRAFT);

        // AI 成本：范围内 token 总用量 × 单价（元 / 千 tokens）
        long tokens = agentRunRepository.sumTokensInRange(since, until, tid);
        ops.setAiCost(Math.round(tokens * costPer1k / 1000.0 * 100.0) / 100.0);

        // 知识库命中率（RAG 命中率）：范围内生成的草稿中带引用（命中过知识库）的占比
        long totalInRange = draftRepository.countByCreatedAtBetween(since, until);
        long citedInRange = draftRepository.countWithCitesInRange(since, until);
        double hitRate = totalInRange > 0 ? Math.round(citedInRange * 1000.0 / totalInRange) / 10.0 : 0;
        ops.setKbHitRate(hitRate);

        // CSAT：范围内已解决工单中有评分的平均分（1-5）
        double csatSum = 0;
        int csatCount = 0;
        for (Ticket t : resolved) {
            if (t.getCsatScore() != null) {
                csatSum += t.getCsatScore();
                csatCount++;
            }
        }
        ops.setCsatAvg(csatCount == 0 ? 0 : Math.round(csatSum / csatCount * 10.0) / 10.0);
        ops.setCsatCount(csatCount);

        // 分类分布
        List<BoardVO.NameCountVO> categoryDist = new ArrayList<>();
        for (Object[] row : ticketRepository.categoryDist(tid, since, until)) {
            categoryDist.add(nameCount(row[0] == null ? "未分类" : row[0].toString(),
                    row[1] == null ? 0L : ((Number) row[1]).longValue()));
        }
        ops.setCategoryDist(categoryDist);

        // 处理人负载 Top5：assignee_id → 姓名
        List<Object[]> loadRows = ticketRepository.assigneeLoad(tid, since, until);
        List<Long> assigneeIds = loadRows.stream()
                .map(r -> r[0] == null ? null : ((Number) r[0]).longValue())
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        Map<Long, String> nameMap = new HashMap<>();
        if (!assigneeIds.isEmpty()) {
            for (User u : userRepository.findAllById(assigneeIds)) {
                String nm = (u.getName() != null && !u.getName().isBlank()) ? u.getName() : u.getUsername();
                nameMap.put(u.getId(), nm);
            }
        }
        List<BoardVO.NameCountVO> assigneeLoad = new ArrayList<>();
        for (Object[] row : loadRows) {
            Long aid = row[0] == null ? null : ((Number) row[0]).longValue();
            long cnt = row[1] == null ? 0L : ((Number) row[1]).longValue();
            assigneeLoad.add(nameCount(aid == null ? "未分配" : nameMap.getOrDefault(aid, "#" + aid), cnt));
        }
        ops.setAssigneeLoad(assigneeLoad);

        return ops;
    }

    private BoardVO.NameCountVO nameCount(String name, long count) {
        BoardVO.NameCountVO nc = new BoardVO.NameCountVO();
        nc.setName(name);
        nc.setCount(count);
        return nc;
    }
}
