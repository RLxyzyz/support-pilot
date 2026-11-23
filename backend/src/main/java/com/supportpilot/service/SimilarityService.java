package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.common.TenantContext;
import com.supportpilot.common.UserContext;
import com.supportpilot.common.audit.AuditContext;
import com.supportpilot.entity.Ticket;
import com.supportpilot.entity.TicketMessage;
import com.supportpilot.entity.TicketRelation;
import com.supportpilot.entity.TicketSimilarity;
import com.supportpilot.entity.SimilarityConfig;
import com.supportpilot.repository.SimilarityConfigRepository;
import com.supportpilot.repository.TicketMessageRepository;
import com.supportpilot.repository.TicketRelationRepository;
import com.supportpilot.repository.TicketRepository;
import com.supportpilot.repository.TicketSimilarityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * M1 相似工单归组：基于「标题 + 正文」字符 bigram Jaccard 相似度做近邻检测，
 * 命中阈值后归入同一相似组，支持批量回复、合并与阈值配置。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimilarityService {

    private final TicketRepository ticketRepository;
    private final TicketSimilarityRepository similarityRepository;
    private final SimilarityConfigRepository configRepository;
    private final TicketRelationRepository ticketRelationRepository;
    private final TicketMessageRepository ticketMessageRepository;
    private final OperationLogService operationLogService;
    private final TransactionTemplate transactionTemplate;

    private static final double DEFAULT_THRESHOLD = 0.82;
    private static final int DEFAULT_WINDOW_DAYS = 30;
    private static final int MAX_NEIGHBORS = 5;

    @Value("${app.tenant.default-id:1}")
    private Long defaultTenantId;

    // ---------------- 检测与归组 ----------------

    /** 建单后调用：检测相似工单并归组。异常由调用方捕获，不影响建单。 */
    @Transactional
    public void detectAndGroup(Ticket ticket) {
        SimilarityConfig cfg = loadConfig(tenantKey(ticket));
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            return;
        }
        Instant since = Instant.now().minus(cfg.getWindowDays(), ChronoUnit.DAYS);
        Long tid = ticket.getTenant() == null ? null : ticket.getTenant().getId();
        String text = textOf(ticket);
        if (text.isBlank()) {
            return;
        }
        List<Scored> scored = new ArrayList<>();
        for (Ticket c : ticketRepository.findRecentForSimilarity(tid, since)) {
            if (c.getId().equals(ticket.getId())) {
                continue;
            }
            double score = jaccard(text, textOf(c));
            if (score >= cfg.getThreshold()) {
                scored.add(new Scored(c, score));
            }
        }
        if (scored.isEmpty()) {
            return;
        }
        scored.sort(Comparator.comparingDouble((Scored s) -> s.score).reversed());
        List<Scored> top = scored.subList(0, Math.min(MAX_NEIGHBORS, scored.size()));

        Long groupId = ticket.getSimilarityGroupId();
        if (groupId == null) {
            for (Scored s : top) {
                if (s.ticket.getSimilarityGroupId() != null) {
                    groupId = s.ticket.getSimilarityGroupId();
                    break;
                }
            }
        }
        if (groupId == null) {
            groupId = ticket.getId();
        }
        ticket.setSimilarityGroupId(groupId);
        ticketRepository.save(ticket);

        for (Scored s : top) {
            if (s.ticket.getSimilarityGroupId() == null) {
                s.ticket.setSimilarityGroupId(groupId);
                ticketRepository.save(s.ticket);
            }
            if (!similarityRepository.existsByTicketIdAndSimilarTicketId(ticket.getId(), s.ticket.getId())) {
                TicketSimilarity rel = new TicketSimilarity();
                rel.setTicketId(ticket.getId());
                rel.setSimilarTicketId(s.ticket.getId());
                rel.setScore(round(s.score));
                rel.setChannel("TEXT");
                rel.setGroupId(groupId);
                rel.setCreatedAt(Instant.now());
                similarityRepository.save(rel);
            }
        }
        log.info("[相似归组] 工单 #{} 归入组 {}，命中 {} 条相似工单", ticket.getId(), groupId, top.size());
    }

    /** GET /tickets/{id}/similar。 */
    @Transactional(readOnly = true)
    public Map<String, Object> listSimilar(Long ticketId) {
        Ticket t = ticketRepository.findById(ticketId)
                .orElseThrow(() -> BusinessException.notFound("工单不存在"));
        Long groupId = t.getSimilarityGroupId();
        if (groupId == null) {
            groupId = similarityRepository.findFirstByTicketIdAndGroupIdIsNotNull(ticketId)
                    .map(TicketSimilarity::getGroupId)
                    .orElseGet(() -> similarityRepository.findBySimilarTicketId(ticketId).stream()
                            .findFirst().map(TicketSimilarity::getGroupId).orElse(null));
        }
        if (groupId == null) {
            return Map.of("groupId", null, "count", 0, "items", List.of());
        }
        List<TicketSimilarity> rows = similarityRepository.findByGroupId(groupId);
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(groupId);
        Map<String, Double> scoreMap = new HashMap<>();
        for (TicketSimilarity r : rows) {
            ids.add(r.getTicketId());
            ids.add(r.getSimilarTicketId());
            scoreMap.merge(r.getTicketId() + "-" + r.getSimilarTicketId(), r.getScore(), Math::max);
        }
        Map<Long, Ticket> ticketMap = new HashMap<>();
        ticketRepository.findAllById(ids).forEach(x -> ticketMap.put(x.getId(), x));

        List<Map<String, Object>> items = new ArrayList<>();
        for (Long id : ids) {
            Ticket x = ticketMap.get(id);
            if (x == null) {
                continue;
            }
            double score = id.equals(groupId) ? 1.0 : Math.max(
                    scoreMap.getOrDefault(ticketId + "-" + id, 0.0),
                    scoreMap.getOrDefault(id + "-" + ticketId, 0.0));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", "#" + id);
            item.put("title", x.getTitle());
            item.put("status", x.getStatus());
            item.put("score", round(score));
            item.put("isMain", id.equals(groupId));
            items.add(item);
        }
        items.sort(Comparator
                .comparing((Map<String, Object> m) -> !Boolean.TRUE.equals(m.get("isMain")))
                .thenComparing(m -> String.valueOf(m.get("id"))));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("groupId", groupId);
        result.put("count", items.size());
        result.put("items", items);
        return result;
    }

    /**
     * 批量回复：对组内（或指定）工单逐单独立事务发送可见回复。
     * 返回 sent 与 failed 明细，单条失败不影响其余工单。
     */
    public Map<String, Object> batchReply(Long ticketId, String content, List<Long> ticketIds) {
        if (content == null || content.isBlank()) {
            throw BusinessException.badRequest("回复内容不能为空");
        }
        List<Long> targets = (ticketIds == null || ticketIds.isEmpty())
                ? groupMemberIds(ticketId) : new ArrayList<>(new LinkedHashSet<>(ticketIds));
        if (targets.isEmpty()) {
            throw BusinessException.badRequest("该工单暂无可批量回复的相似工单");
        }
        UserContext.CurrentUser me = UserContext.get();
        int sent = 0;
        List<Map<String, Object>> failed = new ArrayList<>();
        for (Long id : targets) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    Ticket t = ticketRepository.findById(id)
                            .orElseThrow(() -> BusinessException.notFound("工单不存在"));
                    TicketMessage m = new TicketMessage();
                    m.setTicket(t);
                    m.setWho(me == null || me.getName() == null || me.getName().isBlank()
                            ? "系统" : me.getName());
                    m.setRole(me != null && "admin".equalsIgnoreCase(me.getRole()) ? "管理员" : "工程师");
                    m.setText(content.trim());
                    m.setInternal(false);
                    m.setCreatedAt(Instant.now());
                    ticketMessageRepository.save(m);
                    t.setUpdateTime("刚刚");
                    ticketRepository.save(t);
                });
                sent++;
                audit(me, id, "相似工单批量回复");
            } catch (Exception e) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("id", "#" + id);
                f.put("message", e.getMessage() == null ? "回复失败" : e.getMessage());
                failed.add(f);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sent", sent);
        result.put("failed", failed);
        return result;
    }

    /** 合并：当前工单与目标工单建立 duplicate 关联，并把目标挂到当前工单下。 */
    @Transactional
    public Map<String, Object> merge(Long ticketId, Long targetId) {
        if (targetId == null || targetId.equals(ticketId)) {
            throw BusinessException.badRequest("合并目标工单无效");
        }
        ticketRepository.findById(ticketId)
                .orElseThrow(() -> BusinessException.notFound("工单不存在"));
        Ticket target = ticketRepository.findById(targetId)
                .orElseThrow(() -> BusinessException.badRequest("目标工单不存在"));
        if (!ticketRelationRepository.existsByTicketIdAndRelatedTicketId(ticketId, targetId)) {
            TicketRelation rel = new TicketRelation();
            rel.setTicketId(ticketId);
            rel.setRelatedTicketId(targetId);
            rel.setRelationType("duplicate");
            rel.setCreatedAt(Instant.now());
            ticketRelationRepository.save(rel);
        }
        target.setParentId(ticketId);
        ticketRepository.save(target);
        return Map.of("merged", true, "parentId", ticketId, "targetId", targetId);
    }

    // ---------------- 配置 ----------------

    public SimilarityConfig getConfig() {
        return loadConfig(tenantKey(null));
    }

    @Transactional
    public SimilarityConfig updateConfig(Double threshold, Boolean enabled, Integer windowDays) {
        Long key = tenantKey(null);
        SimilarityConfig cfg = configRepository.findByTenantId(key).orElseGet(() -> {
            SimilarityConfig c = new SimilarityConfig();
            c.setTenantId(key);
            return c;
        });
        if (threshold != null) {
            if (threshold < 0.5 || threshold > 1.0) {
                throw BusinessException.badRequest("相似度阈值需在 0.5 - 1.0 之间");
            }
            cfg.setThreshold(threshold);
        }
        if (enabled != null) {
            cfg.setEnabled(enabled);
        }
        if (windowDays != null) {
            if (windowDays < 1 || windowDays > 365) {
                throw BusinessException.badRequest("检测窗口需在 1 - 365 天之间");
            }
            cfg.setWindowDays(windowDays);
        }
        if (cfg.getThreshold() == null) {
            cfg.setThreshold(DEFAULT_THRESHOLD);
        }
        if (cfg.getEnabled() == null) {
            cfg.setEnabled(true);
        }
        if (cfg.getWindowDays() == null) {
            cfg.setWindowDays(DEFAULT_WINDOW_DAYS);
        }
        return configRepository.save(cfg);
    }

    // ---------------- 内部工具 ----------------

    private List<Long> groupMemberIds(Long ticketId) {
        Map<String, Object> similar = listSimilar(ticketId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) similar.get("items");
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> item : items) {
            String raw = String.valueOf(item.get("id")).replace("#", "");
            try {
                ids.add(Long.valueOf(raw));
            } catch (NumberFormatException ignoredEx) {
                // 跳过非法 id
            }
        }
        if (ids.isEmpty()) {
            ids.add(ticketId);
        }
        return ids;
    }

    private SimilarityConfig loadConfig(Long tenantKey) {
        Long key = tenantKey == null ? defaultTenantId : tenantKey;
        SimilarityConfig cfg = configRepository.findByTenantId(key).orElseGet(SimilarityConfig::new);
        if (cfg.getThreshold() == null) {
            cfg.setThreshold(DEFAULT_THRESHOLD);
        }
        if (cfg.getEnabled() == null) {
            cfg.setEnabled(true);
        }
        if (cfg.getWindowDays() == null) {
            cfg.setWindowDays(DEFAULT_WINDOW_DAYS);
        }
        return cfg;
    }

    private Long tenantKey(Ticket ticket) {
        if (ticket != null && ticket.getTenant() != null) {
            return ticket.getTenant().getId();
        }
        Long tid = TenantContext.get();
        return tid == null ? defaultTenantId : tid;
    }

    private static String textOf(Ticket t) {
        return (t.getTitle() == null ? "" : t.getTitle()) + " " + (t.getBody() == null ? "" : t.getBody());
    }

    /** 字符 bigram 集合（去空白与标点，保留中文与字母数字）。 */
    private static Set<String> bigrams(String s) {
        Set<String> set = new HashSet<>();
        if (s == null) {
            return set;
        }
        String norm = s.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}]", "");
        if (norm.isEmpty()) {
            return set;
        }
        if (norm.length() == 1) {
            set.add(norm);
            return set;
        }
        for (int i = 0; i + 1 < norm.length(); i++) {
            set.add(norm.substring(i, i + 2));
        }
        return set;
    }

    private static double jaccard(String a, String b) {
        Set<String> x = bigrams(a);
        Set<String> y = bigrams(b);
        if (x.isEmpty() || y.isEmpty()) {
            return 0;
        }
        int inter = 0;
        for (String s : x) {
            if (y.contains(s)) {
                inter++;
            }
        }
        int union = x.size() + y.size() - inter;
        return union == 0 ? 0 : (double) inter / union;
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private void audit(UserContext.CurrentUser me, Long ticketId, String summary) {
        try {
            AuditContext ctx = new AuditContext(
                    me == null ? null : me.getId(), me == null ? null : me.getName(),
                    me == null ? null : me.getUsername(), TenantContext.get(),
                    "ticket", "batch-reply", "ticket", String.valueOf(ticketId), summary);
            operationLogService.record(ctx, null, null, null, true, null, 0);
        } catch (Exception ignored) {
            // 审计失败不影响批量回复结果
        }
    }

    private static final class Scored {
        private final Ticket ticket;
        private final double score;

        private Scored(Ticket ticket, double score) {
            this.ticket = ticket;
            this.score = score;
        }
    }
}
