package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.common.TimeFormat;
import com.supportpilot.entity.Ticket;
import com.supportpilot.entity.TicketMessage;
import com.supportpilot.entity.TicketSummary;
import com.supportpilot.repository.TicketMessageRepository;
import com.supportpilot.repository.TicketRepository;
import com.supportpilot.repository.TicketSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M6 会话摘要：消息达到 10 条后自动生成结构化摘要，支持人工修正并保留原版。
 * 无大模型时使用规则模板降级。
 */
@Service
@RequiredArgsConstructor
public class SummaryService {

    private static final int MIN_MESSAGES = 10;

    private final TicketRepository ticketRepository;
    private final TicketMessageRepository ticketMessageRepository;
    private final TicketSummaryRepository summaryRepository;

    /** GET /tickets/{id}/summary。 */
    @Transactional
    public Map<String, Object> get(Long ticketId) {
        ticketRepository.findById(ticketId)
                .orElseThrow(() -> BusinessException.notFound("工单不存在"));
        List<TicketMessage> messages = ticketMessageRepository.findByTicketIdOrderByIdAsc(ticketId);
        TicketSummary s = summaryRepository.findByTicketId(ticketId).orElse(null);
        if (s != null && (notBlank(s.getEditedContent()) || notBlank(s.getOriginalContent()))) {
            return toVO(s, messages.size());
        }
        if (messages.size() < MIN_MESSAGES) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("summary", null);
            m.put("insufficient", true);
            m.put("messageCount", messages.size());
            m.put("message", "会话消息不足 " + MIN_MESSAGES + " 条，暂不生成摘要");
            return m;
        }
        String content = generate(messages);
        if (s == null) {
            s = new TicketSummary();
            s.setTicketId(ticketId);
        }
        s.setOriginalContent(content);
        s.setGeneratedAt(Instant.now());
        s.setUpdatedAt(Instant.now());
        summaryRepository.save(s);
        return toVO(s, messages.size());
    }

    /** PUT /tickets/{id}/summary：保存人工修正版，保留原版。 */
    @Transactional
    public Map<String, Object> update(Long ticketId, String content) {
        if (!notBlank(content)) {
            throw BusinessException.badRequest("摘要内容不能为空");
        }
        TicketSummary s = summaryRepository.findByTicketId(ticketId).orElseGet(() -> {
            TicketSummary x = new TicketSummary();
            x.setTicketId(ticketId);
            return x;
        });
        if (!notBlank(s.getOriginalContent())) {
            List<TicketMessage> messages = ticketMessageRepository.findByTicketIdOrderByIdAsc(ticketId);
            s.setOriginalContent(generate(messages));
            s.setGeneratedAt(Instant.now());
        }
        s.setEditedContent(content.trim());
        s.setUpdatedAt(Instant.now());
        summaryRepository.save(s);
        return toVO(s, ticketMessageRepository.findByTicketIdOrderByIdAsc(ticketId).size());
    }

    /** 供 TicketVO 填充：只读已有摘要，不触发生成。 */
    @Transactional(readOnly = true)
    public String summaryText(Long ticketId) {
        return summaryRepository.findByTicketId(ticketId)
                .map(s -> notBlank(s.getEditedContent()) ? s.getEditedContent() : s.getOriginalContent())
                .orElse(null);
    }

    // ---------------- 规则模板 ----------------

    private String generate(List<TicketMessage> messages) {
        String overview = firstOf(messages, false, 120);
        String customerAsk = lastOf(messages, false, 80);
        List<String> attempts = new ArrayList<>();
        for (TicketMessage m : messages) {
            if (!Boolean.TRUE.equals(m.getInternal()) && "工程师".equals(m.getRole())) {
                attempts.add(clip(m.getText(), 40));
            }
            if (attempts.size() >= 3) {
                break;
            }
        }
        String block = lastOf(messages, true, 80);
        StringBuilder sb = new StringBuilder();
        sb.append("【问题概述】").append(overview.isEmpty() ? "客户问题待补充" : overview).append('\n');
        sb.append("【已做尝试】").append(attempts.isEmpty() ? "暂无工程师回复" : String.join("；", attempts)).append('\n');
        sb.append("【当前卡点】").append(block.isEmpty() ? "等待客户进一步反馈" : block).append('\n');
        sb.append("【客户诉求】").append(customerAsk.isEmpty() ? "尽快解决问题" : customerAsk).append('\n');
        sb.append("【下一步建议】按 SLA 时限跟进，补充排查结论并同步客户进展。");
        return sb.toString();
    }

    private String firstOf(List<TicketMessage> messages, boolean internal, int max) {
        for (TicketMessage m : messages) {
            if (Boolean.TRUE.equals(m.getInternal()) == internal && notBlank(m.getText())) {
                return clip(m.getText(), max);
            }
        }
        return "";
    }

    private String lastOf(List<TicketMessage> messages, boolean internal, int max) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            TicketMessage m = messages.get(i);
            if (Boolean.TRUE.equals(m.getInternal()) == internal && notBlank(m.getText())) {
                return clip(m.getText(), max);
            }
        }
        return "";
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() <= max ? one : one.substring(0, max) + "…";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private Map<String, Object> toVO(TicketSummary s, int messageCount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("summary", notBlank(s.getEditedContent()) ? s.getEditedContent() : s.getOriginalContent());
        m.put("edited", notBlank(s.getEditedContent()));
        m.put("insufficient", false);
        m.put("messageCount", messageCount);
        m.put("generatedAt", TimeFormat.format(s.getGeneratedAt()));
        return m;
    }
}
