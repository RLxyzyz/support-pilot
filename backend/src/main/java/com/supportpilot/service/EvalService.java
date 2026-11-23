package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.common.TenantContext;
import com.supportpilot.entity.AiEvalCase;
import com.supportpilot.entity.AiEvalRun;
import com.supportpilot.entity.Draft;
import com.supportpilot.entity.Ticket;
import com.supportpilot.repository.AiEvalCaseRepository;
import com.supportpilot.repository.AiEvalRunRepository;
import com.supportpilot.repository.DraftRepository;
import com.supportpilot.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * M3 AI 草稿采纳与评测：采纳率统计、驳回原因校验与用例沉淀、回归评测与低分聚类。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalService {

    /** 合法驳回原因（FR-M3-02）。 */
    public static final List<String> REJECT_REASONS = List.of(
            "信息不完整", "结论不准确", "语气不合适", "知识库缺失", "其他");

    private static final int MIN_EVAL_CASES = 30;

    private final DraftRepository draftRepository;
    private final TicketRepository ticketRepository;
    private final AiEvalCaseRepository caseRepository;
    private final AiEvalRunRepository runRepository;

    @Value("${app.tenant.default-id:1}")
    private Long defaultTenantId;

    // ---------------- 采纳率统计 ----------------

    /** 按分类、按工程师聚合草稿采纳 / 驳回 / 编辑比例。 */
    @Transactional(readOnly = true)
    public Map<String, Object> adoption() {
        List<Draft> drafts = draftRepository.findAll();
        Map<Long, Ticket> ticketMap = new HashMap<>();
        Set<Long> ticketIds = new HashSet<>();
        drafts.forEach(d -> {
            if (d.getTicketId() != null) {
                ticketIds.add(d.getTicketId());
            }
        });
        ticketRepository.findAllById(ticketIds).forEach(t -> ticketMap.put(t.getId(), t));

        Map<String, int[]> byCategory = new LinkedHashMap<>();
        Map<String, int[]> byEngineer = new LinkedHashMap<>();
        for (Draft d : drafts) {
            Ticket t = ticketMap.get(d.getTicketId());
            String category = t == null || t.getCategory() == null ? "未分类" : t.getCategory();
            String engineer = t == null || t.getAssigneeId() == null ? "未分配" : String.valueOf(t.getAssigneeId());
            accumulate(byCategory, category, d);
            accumulate(byEngineer, engineer, d);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", drafts.size());
        result.put("byCategory", toStats(byCategory));
        result.put("byEngineer", toStats(byEngineer));
        return result;
    }

    private void accumulate(Map<String, int[]> map, String key, Draft d) {
        int[] v = map.computeIfAbsent(key, k -> new int[3]); // total / adopted / rejected
        v[0]++;
        if ("adopted".equalsIgnoreCase(d.getStatus())) {
            v[1]++;
        } else if ("rejected".equalsIgnoreCase(d.getStatus())) {
            v[2]++;
        }
    }

    private List<Map<String, Object>> toStats(Map<String, int[]> map) {
        List<Map<String, Object>> list = new ArrayList<>();
        map.forEach((key, v) -> {
            int total = v[0];
            int adopted = v[1];
            int rejected = v[2];
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", key);
            m.put("total", total);
            m.put("adopted", adopted);
            m.put("rejected", rejected);
            m.put("adoptionRate", total == 0 ? 0 : round((double) adopted / total));
            m.put("rejectRate", total == 0 ? 0 : round((double) rejected / total));
            list.add(m);
        });
        list.sort(Comparator.comparingInt(m -> -((Number) m.get("total")).intValue()));
        return list;
    }

    // ---------------- 驳回原因与用例沉淀 ----------------

    /** 校验驳回原因：非空、枚举内，「其他」必须填写备注。 */
    public void validateRejectReason(String reason, String comment) {
        if (reason == null || reason.isBlank()) {
            throw BusinessException.badRequest("请选择驳回原因");
        }
        if (!REJECT_REASONS.contains(reason.trim())) {
            throw BusinessException.badRequest("非法的驳回原因：" + reason);
        }
        if ("其他".equals(reason.trim()) && (comment == null || comment.isBlank())) {
            throw BusinessException.badRequest("选择「其他」时必须填写驳回备注");
        }
    }

    /** 驳回时沉淀一条评测用例（FR-M3-02）。 */
    @Transactional
    public void onDraftRejected(Long ticketId, String reason, String comment) {
        String question = ticketRepository.findById(ticketId)
                .map(Ticket::getTitle).orElse(null);
        AiEvalCase c = new AiEvalCase();
        c.setTicketId(ticketId);
        c.setQuestion(question);
        c.setRejectReason(reason);
        c.setAcceptCriteria("人工修正后的回复应覆盖客户问题并纠正：" + (comment == null ? reason : comment));
        c.setStatus("ACTIVE");
        c.setTenantId(tenantKey());
        c.setCreatedAt(Instant.now());
        caseRepository.save(c);
    }

    /** 从草稿 / 工单沉淀评测用例（FR-M3-03）。 */
    @Transactional
    public AiEvalCase sediment(Long ticketId, Long draftId, String question, String expectedPoints) {
        AiEvalCase c = new AiEvalCase();
        Long tid = ticketId;
        if (draftId != null) {
            Draft d = draftRepository.findById(draftId)
                    .orElseThrow(() -> BusinessException.notFound("草稿不存在"));
            tid = d.getTicketId();
            if (expectedPoints == null || expectedPoints.isBlank()) {
                expectedPoints = d.getEditedContent() != null && !d.getEditedContent().isBlank()
                        ? d.getEditedContent() : d.getContent();
            }
        }
        if (tid != null) {
            c.setTicketId(tid);
        }
        if (question == null || question.isBlank()) {
            question = tid == null ? "人工沉淀用例"
                    : ticketRepository.findById(tid).map(Ticket::getTitle).orElse("人工沉淀用例");
        }
        if (expectedPoints == null || expectedPoints.isBlank()) {
            throw BusinessException.badRequest("请提供期望要点");
        }
        c.setQuestion(question);
        c.setExpectedPoints(expectedPoints);
        c.setAcceptCriteria("回复需覆盖期望要点中的关键结论");
        c.setStatus("ACTIVE");
        c.setTenantId(tenantKey());
        c.setCreatedAt(Instant.now());
        return caseRepository.save(c);
    }

    @Transactional(readOnly = true)
    public List<AiEvalCase> cases() {
        Long tid = TenantContext.get();
        return tid == null ? caseRepository.findAllByOrderByIdDesc()
                : caseRepository.findByTenantIdOrderByIdDesc(tid);
    }

    // ---------------- 回归评测 ----------------

    /**
     * 执行一次回归评测：用例不足 30 条返回 400；用「关键词覆盖率」判定通过。
     */
    @Transactional
    public Map<String, Object> run(String versionLabel) {
        List<AiEvalCase> cases = cases();
        if (cases.size() < MIN_EVAL_CASES) {
            throw BusinessException.badRequest("评测用例不足 " + MIN_EVAL_CASES + " 条（当前 " + cases.size() + " 条）");
        }
        int passed = 0;
        List<Map<String, Object>> details = new ArrayList<>();
        for (AiEvalCase c : cases) {
            boolean ok = judge(c);
            if (ok) {
                passed++;
            }
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("caseId", c.getId());
            d.put("question", c.getQuestion());
            d.put("passed", ok);
            details.add(d);
        }
        double passRate = round((double) passed / cases.size());
        Double prev = runRepository.findTopByOrderByIdDesc()
                .map(AiEvalRun::getPassRate).orElse(null);

        AiEvalRun run = new AiEvalRun();
        run.setVersionLabel(versionLabel == null || versionLabel.isBlank()
                ? "v" + Instant.now().toEpochMilli() : versionLabel);
        run.setTotal(cases.size());
        run.setPassed(passed);
        run.setPassRate(passRate);
        run.setPrevPassRate(prev);
        run.setDetailJson(toJson(details));
        run.setCreatedAt(Instant.now());
        runRepository.save(run);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("versionLabel", run.getVersionLabel());
        result.put("total", run.getTotal());
        result.put("passed", run.getPassed());
        result.put("passRate", passRate);
        result.put("prevPassRate", prev);
        result.put("delta", prev == null ? null : round(passRate - prev));
        return result;
    }

    /** 判定：期望要点与问题关键词的覆盖率 ≥ 0.3 视为通过（无大模型时的确定性兜底）。 */
    private boolean judge(AiEvalCase c) {
        String question = c.getQuestion() == null ? "" : c.getQuestion();
        String expected = (c.getExpectedPoints() == null ? "" : c.getExpectedPoints())
                + " " + (c.getAcceptCriteria() == null ? "" : c.getAcceptCriteria());
        Set<String> keywords = keywords(question);
        if (keywords.isEmpty()) {
            return expected.length() >= 10;
        }
        int hit = 0;
        for (String k : keywords) {
            if (expected.toLowerCase(Locale.ROOT).contains(k)) {
                hit++;
            }
        }
        return (double) hit / keywords.size() >= 0.3;
    }

    private Set<String> keywords(String text) {
        Set<String> set = new HashSet<>();
        String norm = text.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}]", "");
        for (int i = 0; i + 1 < norm.length(); i++) {
            set.add(norm.substring(i, i + 2));
            if (set.size() >= 40) {
                break;
            }
        }
        return set;
    }

    /** 低分聚类：按驳回原因聚合用例数（FR-M3-06）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> clusters() {
        Map<String, Integer> counter = new LinkedHashMap<>();
        for (AiEvalCase c : cases()) {
            if (c.getRejectReason() == null || c.getRejectReason().isBlank()) {
                continue;
            }
            counter.merge(c.getRejectReason(), 1, Integer::sum);
        }
        List<Map<String, Object>> list = new ArrayList<>();
        counter.forEach((reason, count) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reason", reason);
            m.put("count", count);
            list.add(m);
        });
        list.sort(Comparator.comparingInt(m -> -((Number) m.get("count")).intValue()));
        return list;
    }

    private String toJson(List<Map<String, Object>> details) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < details.size(); i++) {
            Map<String, Object> d = details.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"caseId\":").append(d.get("caseId"))
                    .append(",\"passed\":").append(d.get("passed")).append('}');
        }
        return sb.append(']').toString();
    }

    private Long tenantKey() {
        Long tid = TenantContext.get();
        return tid == null ? defaultTenantId : tid;
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
