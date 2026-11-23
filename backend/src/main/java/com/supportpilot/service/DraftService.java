package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.common.UserContext;
import com.supportpilot.dto.DraftFeedbackStatsVO;
import com.supportpilot.entity.Draft;
import com.supportpilot.entity.Ticket;
import com.supportpilot.entity.TicketMessage;
import com.supportpilot.repository.DraftRepository;
import com.supportpilot.repository.KbDocRepository;
import com.supportpilot.repository.TicketMessageRepository;
import com.supportpilot.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 草稿采纳 / 驳回：采纳后把（可能经人工编辑的）草稿内容作为一条对客户可见的回复写入
 * ticket_message，同时将工单状态持久化为「已解决」并停止 SLA 计时。
 * 草稿 id 兼容前端传入的 d_{ticketId} 与数字 ticketId。
 *
 * <p>反馈闭环：驳回记录原因/备注；采纳记录最终编辑内容；采纳后回填被引用文档的命中次数。
 */
@Service
@RequiredArgsConstructor
public class DraftService {

    private final TicketRepository ticketRepository;
    private final DraftRepository draftRepository;
    private final TicketMessageRepository ticketMessageRepository;
    private final KbDocRepository kbDocRepository;
    private final KbService kbService;
    private final SlaService slaService;
    private final EvalService evalService;

    private static final String APPROVE_COST = "耗时 3 分 12 秒 · ¥0.028";

    @Transactional
    public Map<String, Object> approve(String rawId, String content) {
        Long ticketId = parseTicketId(rawId);
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> BusinessException.notFound("工单不存在"));
        String oldStatus = ticket.getStatus();

        // 采纳即回复：把（可能经人工编辑的）草稿内容作为一条对客户可见的消息写入会话，
        // 避免出现「状态已解决但会话里没有回复」的数据断裂。
        String reply = content == null ? "" : content.trim();
        if (!reply.isEmpty()) {
            TicketMessage msg = new TicketMessage();
            msg.setTicket(ticket);
            UserContext.CurrentUser me = UserContext.get();
            msg.setWho(displayName(me));
            msg.setRole(me != null && "admin".equalsIgnoreCase(me.getRole()) ? "管理员" : "工程师");
            msg.setText(reply);
            msg.setInternal(false);
            ticketMessageRepository.save(msg);
        }

        ticket.setStatus("已解决");
        ticket.setStatusClass("st-res");
        ticket.setSla("已停止计时");
        ticket.setSlaPct(100);
        ticket.setSlaColor("#cbd5e1");
        if (ticket.getResolvedAt() == null) {
            ticket.setResolvedAt(Instant.now());
        }
        slaService.onStatusChange(ticket, oldStatus, "已解决");
        ticket.setUpdateTime("刚刚");
        ticketRepository.save(ticket);

        Draft draft = draftRepository.findFirstByTicketIdOrderByIdDesc(ticketId)
                .orElseGet(() -> {
                    Draft d = new Draft();
                    d.setTicketId(ticketId);
                    return d;
                });
        String original = draft.getContent() == null ? "" : draft.getContent().trim();
        draft.setStatus("adopted");
        draft.setContent(reply); // 记录最终采纳的回复内容，便于审计与回溯
        // 若人工编辑后的回复与 AI 原始草稿不同，留存编辑内容用于反馈分析
        if (!reply.isEmpty() && !reply.equals(original)) {
            draft.setEditedContent(reply);
        }
        draft.setCost(APPROVE_COST);
        fillVersionDefaults(draft);
        draftRepository.save(draft);

        // 回填被引用文档的命中次数（采纳即视为一次有效命中），失败不影响主流程
        incrementCitedHits(draft.getCitedDocIds());

        // 解决后异步沉淀进知识库（事务提交后执行，含本次回复消息），失败不影响主流程
        kbService.ingestTicketAsync(ticketId);

        return Map.of("adopted", true, "cost", APPROVE_COST);
    }

    /** 解析 cited_doc_ids（逗号分隔）并对每个文档命中次数 +1。 */
    private void incrementCitedHits(String citedDocIds) {
        if (citedDocIds == null || citedDocIds.isBlank()) {
            return;
        }
        try {
            for (String s : citedDocIds.split(",")) {
                Long id = Long.valueOf(s.trim());
                kbDocRepository.incrementHits(id);
            }
        } catch (Exception e) {
            // 忽略：命中统计失败不应阻断采纳
        }
    }

    private String displayName(UserContext.CurrentUser me) {
        if (me == null) {
            return "系统";
        }
        if (me.getName() != null && !me.getName().isBlank()) {
            return me.getName();
        }
        return me.getUsername() == null ? "系统" : me.getUsername();
    }

    @Transactional
    public Map<String, Object> reject(String rawId, String reason, String comment) {
        // M3：驳回原因枚举校验（「其他」必填备注）
        evalService.validateRejectReason(reason, comment);
        Long ticketId = parseTicketId(rawId);
        draftRepository.findFirstByTicketIdOrderByIdDesc(ticketId).ifPresent(d -> {
            d.setStatus("rejected");
            d.setFeedbackReason(reason == null || reason.isBlank() ? null : reason.trim());
            d.setFeedbackComment(comment == null || comment.isBlank() ? null : comment.trim());
            fillVersionDefaults(d);
            draftRepository.save(d);
        });
        // 驳回沉淀为评测用例
        evalService.onDraftRejected(ticketId, reason.trim(), comment);
        return Map.of("rejected", true);
    }

    /** M3：补齐模型 / 提示词 / 知识库版本与置信度默认值，保证评测可追溯。 */
    private void fillVersionDefaults(Draft draft) {
        if (draft.getConfidence() == null) {
            draft.setConfidence(0.8);
        }
        if (draft.getModelVersion() == null) {
            draft.setModelVersion("supportpilot-v1");
        }
        if (draft.getPromptVersion() == null) {
            draft.setPromptVersion("p1");
        }
        if (draft.getKbVersion() == null) {
            draft.setKbVersion("kb-latest");
        }
    }

    /** 草稿反馈统计（采纳 / 驳回 / 被编辑 / 驳回原因分布），供看板展示。 */
    public DraftFeedbackStatsVO feedbackStats() {
        DraftFeedbackStatsVO vo = new DraftFeedbackStatsVO();
        vo.setAdopted(draftRepository.countByStatus("adopted"));
        vo.setRejected(draftRepository.countByStatus("rejected"));
        vo.setEdited(draftRepository.countEdited());
        List<DraftFeedbackStatsVO.ReasonVO> reasons = new ArrayList<>();
        for (Object[] row : draftRepository.rejectReasonDist()) {
            DraftFeedbackStatsVO.ReasonVO r = new DraftFeedbackStatsVO.ReasonVO();
            r.setReason(row[0] == null ? "未填写" : row[0].toString());
            r.setCount(row[1] == null ? 0L : ((Number) row[1]).longValue());
            reasons.add(r);
        }
        vo.setReasons(reasons);
        return vo;
    }

    private Long parseTicketId(String rawId) {
        try {
            String s = rawId.replace("d_", "").replace("#", "").trim();
            return Long.valueOf(s);
        } catch (Exception e) {
            throw BusinessException.badRequest("无效的草稿编号：" + rawId);
        }
    }
}
