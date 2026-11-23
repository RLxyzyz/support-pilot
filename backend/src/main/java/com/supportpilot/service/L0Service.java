package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.common.DisplayUtil;
import com.supportpilot.common.TenantContext;
import com.supportpilot.entity.AutoResolveConfig;
import com.supportpilot.entity.Ticket;
import com.supportpilot.entity.TicketMessage;
import com.supportpilot.repository.AutoResolveConfigRepository;
import com.supportpilot.repository.TicketMessageRepository;
import com.supportpilot.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * M4 L0 自助闭环：白名单分类 + 置信度阈值驱动自动应答，支持语义拦截、客户确认、
 * 重开与转人工，并提供 L0 效果报表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class L0Service {

    public static final String STATE_AUTO_REPLIED = "AUTO_REPLIED";
    public static final String STATE_TRANSFERRED = "TRANSFERRED";
    public static final String STATE_CONFIRMED = "CONFIRMED";
    public static final String STATE_REOPENED = "REOPENED";

    /** 语义拦截词：命中即转人工。 */
    private static final List<String> BLOCK_WORDS = List.of(
            "退费", "退款", "赔偿", "投诉", "合同", "解约", "法务", "起诉");

    private final AutoResolveConfigRepository configRepository;
    private final TicketRepository ticketRepository;
    private final TicketMessageRepository ticketMessageRepository;
    private final SimilarityService similarityService;

    @Value("${app.tenant.default-id:1}")
    private Long defaultTenantId;

    // ---------------- 建单后自动应答 ----------------

    /**
     * 建单后尝试 L0 自动应答。异常由调用方捕获，不影响建单。
     */
    @Transactional
    public void attemptAutoResolve(Ticket ticket) {
        Long tid = tenantKey(ticket);
        List<AutoResolveConfig> configs = configRepository.findByTenantIdOrderByIdAsc(tid);
        Optional<AutoResolveConfig> cfg = configs.stream()
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
                .filter(c -> ticket.getCategory() != null && ticket.getCategory().equals(c.getCategory()))
                .findFirst();
        if (cfg.isEmpty()) {
            return;
        }
        String text = (ticket.getTitle() == null ? "" : ticket.getTitle()) + " "
                + (ticket.getBody() == null ? "" : ticket.getBody());
        String blocked = blockedWord(text);
        if (blocked != null) {
            ticket.setL0State(STATE_TRANSFERRED);
            ticket.setL0HandlerName("AI 助手");
            ticketRepository.save(ticket);
            appendMessage(ticket, "系统", "系统",
                    "语义拦截：命中「" + blocked + "」，已转人工处理。", true);
            log.info("[L0] 工单 #{} 命中拦截词「{}」，转人工", ticket.getId(), blocked);
            return;
        }
        double confidence = estimateConfidence(ticket, text);
        double threshold = cfg.get().getThreshold() == null ? 0.9 : cfg.get().getThreshold();
        if (confidence < threshold) {
            log.info("[L0] 工单 #{} 未自动应答：置信度 {} < 阈值 {}（分类 {}，描述 {} 字）",
                    ticket.getId(), confidence, threshold, ticket.getCategory(),
                    ticket.getBody() == null ? 0 : ticket.getBody().length());
            return;
        }
        String token = UUID.randomUUID().toString().replace("-", "")
                + "." + Instant.now().plusSeconds(72 * 3600).getEpochSecond();
        ticket.setStatus("AI 处理中");
        ticket.setStatusClass(DisplayUtil.statusClass("AI 处理中"));
        ticket.setL0State(STATE_AUTO_REPLIED);
        ticket.setL0HandlerName("AI 助手");
        ticket.setL0ConfirmToken(token);
        ticket.setUpdateTime("刚刚");
        ticketRepository.save(ticket);
        appendMessage(ticket, "AI 助手", "AI",
                "您好，已收到您的问题。根据知识库，建议您先尝试：\n"
                        + "1. 确认账号与网络环境正常；\n"
                        + "2. 按帮助中心对应文档逐步排查；\n"
                        + "3. 若问题仍存在，请点击「转人工」联系工程师。\n"
                        + "（本条为 AI 助手自动回复，如已解决请确认关闭。）", false);
        log.info("[L0] 工单 #{} 自动应答成功，置信度 {}", ticket.getId(), confidence);
    }

    /**
     * 规则法置信度估算（无大模型时确定性兜底）。
     *
     * <p>命中白名单分类本身即视为可信场景（基础 0.8），再按描述充分度与是否命中相似组小幅加分，
     * 这样只有标题的工单也能达到默认阈值 0.8；阈值调到 0.9 时则要求填写问题描述。
     */
    private double estimateConfidence(Ticket ticket, String text) {
        int bodyLen = ticket.getBody() == null ? 0 : ticket.getBody().length();
        double c = 0.8;
        if (bodyLen >= 20) {
            c += 0.08;
        }
        if (bodyLen >= 60) {
            c += 0.04;
        }
        if (text.length() >= 40) {
            c += 0.04;
        }
        if (ticket.getSimilarityGroupId() != null) {
            c += 0.03;
        }
        return Math.min(0.99, Math.round(c * 100.0) / 100.0);
    }

    private String blockedWord(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String w : BLOCK_WORDS) {
            if (lower.contains(w)) {
                return w;
            }
        }
        return null;
    }

    // ---------------- 客户确认 / 重开 / 转人工 ----------------

    @Transactional
    public Map<String, Object> confirm(String token) {
        if (token == null || token.isBlank()) {
            throw BusinessException.badRequest("确认令牌不能为空");
        }
        String raw = token.trim();
        int dot = raw.lastIndexOf('.');
        if (dot > 0) {
            try {
                long expireAt = Long.parseLong(raw.substring(dot + 1));
                if (Instant.now().getEpochSecond() > expireAt) {
                    throw BusinessException.badRequest("确认链接已过期，请联系工程师");
                }
            } catch (NumberFormatException ignored) {
                // 旧格式 token 无过期后缀，放行
            }
        }
        Ticket t = ticketRepository.findByL0ConfirmToken(raw)
                .orElseThrow(() -> BusinessException.notFound("确认链接无效或已使用"));
        t.setStatus("已解决");
        t.setStatusClass(DisplayUtil.statusClass("已解决"));
        t.setL0State(STATE_CONFIRMED);
        t.setL0ClosedAt(Instant.now());
        t.setResolvedAt(Instant.now());
        t.setL0ConfirmToken(null);
        t.setUpdateTime("刚刚");
        ticketRepository.save(t);
        return Map.of("confirmed", true, "ticketId", "#" + t.getId());
    }

    @Transactional
    public Map<String, Object> reopen(Long ticketId) {
        Ticket t = load(ticketId);
        t.setStatus("待分诊");
        t.setStatusClass(DisplayUtil.statusClass("待分诊"));
        t.setL0State(STATE_REOPENED);
        t.setL0ReopenCount((t.getL0ReopenCount() == null ? 0 : t.getL0ReopenCount()) + 1);
        t.setL0ConfirmToken(null);
        t.setResolvedAt(null);
        t.setUpdateTime("刚刚");
        Long preferred = preferredHandler(t);
        if (preferred != null) {
            t.setAssigneeId(preferred);
        }
        ticketRepository.save(t);
        appendMessage(t, "系统", "系统", "客户反馈未解决，工单已重开并优先派回原处理人。", true);
        return Map.of("reopened", true, "ticketId", "#" + t.getId(),
                "reopenCount", t.getL0ReopenCount());
    }

    @Transactional
    public Map<String, Object> transfer(Long ticketId) {
        Ticket t = load(ticketId);
        t.setStatus("待分诊");
        t.setStatusClass(DisplayUtil.statusClass("待分诊"));
        t.setL0State(STATE_TRANSFERRED);
        t.setL0ConfirmToken(null);
        t.setUpdateTime("刚刚");
        ticketRepository.save(t);
        appendMessage(t, "系统", "系统", "客户选择转人工，工单已回到待分诊队列。", true);
        return Map.of("transferred", true, "ticketId", "#" + t.getId());
    }

    /** 相似组主单处理人优先，其次组内已分配处理人。 */
    private Long preferredHandler(Ticket t) {
        if (t.getSimilarityGroupId() == null) {
            return null;
        }
        Long groupId = t.getSimilarityGroupId();
        Optional<Ticket> main = ticketRepository.findById(groupId);
        if (main.isPresent() && main.get().getAssigneeId() != null) {
            return main.get().getAssigneeId();
        }
        Map<String, Object> similar = similarityService.listSimilar(t.getId());
        Object items = similar.get("items");
        if (items instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) {
                    continue;
                }
                String id = String.valueOf(m.get("id")).replace("#", "");
                try {
                    Long sid = Long.valueOf(id);
                    Optional<Ticket> st = ticketRepository.findById(sid);
                    if (st.isPresent() && st.get().getAssigneeId() != null
                            && !st.get().getId().equals(t.getId())) {
                        return st.get().getAssigneeId();
                    }
                } catch (NumberFormatException ignored) {
                    // 跳过非法 id
                }
            }
        }
        return null;
    }

    // ---------------- 报表 ----------------

    @Transactional(readOnly = true)
    public List<Map<String, Object>> report() {
        Map<String, int[]> agg = new LinkedHashMap<>(); // total / confirmed / reopened / transferred
        for (Ticket t : ticketRepository.findByL0StateIsNotNull()) {
            String category = t.getCategory() == null ? "未分类" : t.getCategory();
            int[] v = agg.computeIfAbsent(category, k -> new int[4]);
            v[0]++;
            String state = t.getL0State();
            if (STATE_CONFIRMED.equals(state) || "已解决".equals(t.getStatus())) {
                v[1]++;
            }
            if (STATE_REOPENED.equals(state)
                    || (t.getL0ReopenCount() != null && t.getL0ReopenCount() > 0)) {
                v[2]++;
            }
            if (STATE_TRANSFERRED.equals(state)) {
                v[3]++;
            }
        }
        List<Map<String, Object>> list = new ArrayList<>();
        agg.forEach((category, v) -> {
            int total = v[0];
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", category);
            m.put("total", total);
            m.put("resolved", v[1]);
            m.put("reopened", v[2]);
            m.put("transferred", v[3]);
            m.put("resolveRate", ratio(v[1], total));
            m.put("miscloseRate", ratio(v[2], total));
            m.put("transferRate", ratio(v[3], total));
            list.add(m);
        });
        list.sort(Comparator.comparingInt(m -> -((Number) m.get("total")).intValue()));
        return list;
    }

    private double ratio(int a, int b) {
        return b == 0 ? 0 : Math.round((double) a / b * 1000.0) / 1000.0;
    }

    // ---------------- 配置 ----------------

    @Transactional(readOnly = true)
    public Map<String, Object> getConfig() {
        Long tid = tenantKey(null);
        List<Map<String, Object>> items = new ArrayList<>();
        for (AutoResolveConfig c : configRepository.findByTenantIdOrderByIdAsc(tid)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", c.getCategory());
            m.put("enabled", c.getEnabled());
            m.put("threshold", c.getThreshold());
            items.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", items.stream().anyMatch(m -> Boolean.TRUE.equals(m.get("enabled"))));
        result.put("categories", items);
        return result;
    }

    /**
     * 更新配置：必须显式 confirm=true（高风险开关），否则 400。
     * items 为 [{category, enabled, threshold}]。
     */
    @Transactional
    public Map<String, Object> updateConfig(Boolean confirm, List<Map<String, Object>> items) {
        if (!Boolean.TRUE.equals(confirm)) {
            throw BusinessException.badRequest("开启 L0 自助应答需二次确认（confirm=true）");
        }
        Long tid = tenantKey(null);
        List<AutoResolveConfig> existing = configRepository.findByTenantIdOrderByIdAsc(tid);
        Map<String, AutoResolveConfig> byCategory = new LinkedHashMap<>();
        existing.forEach(c -> byCategory.put(c.getCategory(), c));
        List<String> keep = new ArrayList<>();
        if (items != null) {
            for (Map<String, Object> item : items) {
                Object cat = item.get("category");
                if (cat == null || String.valueOf(cat).isBlank()) {
                    continue;
                }
                String category = String.valueOf(cat).trim();
                keep.add(category);
                AutoResolveConfig c = byCategory.getOrDefault(category, new AutoResolveConfig());
                c.setTenantId(tid);
                c.setCategory(category);
                c.setEnabled(item.get("enabled") == null || Boolean.TRUE.equals(item.get("enabled")));
                Object th = item.get("threshold");
                if (th instanceof Number n) {
                    double v = n.doubleValue();
                    if (v < 0.5 || v > 1.0) {
                        throw BusinessException.badRequest("置信度阈值需在 0.5 - 1.0 之间");
                    }
                    c.setThreshold(v);
                } else if (c.getThreshold() == null) {
                    c.setThreshold(0.9);
                }
                c.setUpdatedAt(Instant.now());
                configRepository.save(c);
            }
        }
        for (AutoResolveConfig c : existing) {
            if (!keep.contains(c.getCategory())) {
                configRepository.delete(c);
            }
        }
        return getConfig();
    }

    // ---------------- 工具 ----------------

    private Ticket load(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> BusinessException.notFound("工单不存在"));
    }

    private void appendMessage(Ticket t, String who, String role, String text, boolean internal) {
        TicketMessage m = new TicketMessage();
        m.setTicket(t);
        m.setWho(who);
        m.setRole(role);
        m.setText(text);
        m.setInternal(internal);
        m.setCreatedAt(Instant.now());
        ticketMessageRepository.save(m);
    }

    private Long tenantKey(Ticket ticket) {
        if (ticket != null && ticket.getTenant() != null) {
            return ticket.getTenant().getId();
        }
        Long tid = TenantContext.get();
        return tid == null ? defaultTenantId : tid;
    }
}
