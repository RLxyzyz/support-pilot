package com.supportpilot.service;

import com.supportpilot.common.NotifyType;
import com.supportpilot.entity.Notification;
import com.supportpilot.entity.NotifyRule;
import com.supportpilot.entity.Ticket;
import com.supportpilot.entity.User;
import com.supportpilot.repository.NotifyRuleRepository;
import com.supportpilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 通知事件分发器（V2.0 EP3）：事件 → 规则匹配 → 接收人解析 → 站内信 / 邮件。
 *
 * <p>任何异常都在内部吞掉并记录，绝不阻断工单主流程（NFR-4）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotifyRuleRepository notifyRuleRepository;
    private final NotificationService notificationService;
    private final MailService mailService;
    private final UserRepository userRepository;

    /**
     * 分派 / 转派工单事件。
     *
     * @param eventType  {@link NotifyType#TICKET_ASSIGNED} 或 {@link NotifyType#TICKET_TRANSFERRED}
     * @param ticket     工单
     * @param assigneeId 新处理人
     * @param actorName  操作人显示名（用于通知正文）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchTicketEvent(String eventType, Ticket ticket, Long assigneeId, String actorName) {
        try {
            List<NotifyRule> rules = notifyRuleRepository.findByEventTypeAndEnabledTrue(eventType);
            if (rules.isEmpty()) {
                log.debug("[通知] 事件 {} 未配置启用规则，跳过", eventType);
                return;
            }
            Long tenantId = tenantOf(ticket);
            NotifyRule rule = rules.get(0);
            Set<Long> receivers = resolveReceivers(rule.getReceivers(), assigneeId, tenantId);
            if (receivers.isEmpty()) {
                log.debug("[通知] 事件 {} 接收人解析为空，跳过", eventType);
                return;
            }
            String title = truncate("【" + NotifyType.label(eventType) + "】#" + ticket.getId() + " "
                    + nullSafe(ticket.getTitle()), 128);
            String content = truncate(buildContent(eventType, ticket, actorName), 500); // notification.content 列为 512

            boolean inApp = Boolean.TRUE.equals(rule.getInAppEnabled());
            boolean email = Boolean.TRUE.equals(rule.getEmailEnabled());
            if (inApp) {
                List<Notification> saved = notificationService.sendToUsers(
                        receivers, tenantId, eventType, title, content, "ticket", String.valueOf(ticket.getId()));
                if (email) {
                    mailService.enqueue(saved);
                }
            } else if (email) {
                mailService.enqueueForUsers(receivers, title, content);
            }
            log.info("[通知] 事件 {} 已分发：工单 #{} → {} 人（站内信={}, 邮件={}）",
                    eventType, ticket.getId(), receivers.size(), inApp, email);
        } catch (Exception e) {
            log.error("[通知] 事件 {} 分发失败（不影响主流程）：{}", eventType, e.getMessage(), e);
        }
    }

    /**
     * 分派 SLA 预警 / 超时事件（V2.1 M2）：按 conditionJson 的优先级条件过滤规则，
     * 接收人默认处理人 + 主管；无处理人时退化为主管 + 管理员。
     *
     * @param eventType {@link NotifyType#SLA_WARNING} 或 {@link NotifyType#SLA_BREACH}
     * @param ticket    工单
     * @param detail    补充说明（如剩余时长 / 超时时长）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchSlaEvent(String eventType, Ticket ticket, String detail) {
        try {
            List<NotifyRule> rules = notifyRuleRepository.findByEventTypeAndEnabledTrue(eventType);
            if (rules.isEmpty()) {
                log.debug("[通知] SLA 事件 {} 未配置启用规则，跳过", eventType);
                return;
            }
            Long tenantId = tenantOf(ticket);
            String title = truncate("【" + NotifyType.label(eventType) + "】#" + ticket.getId() + " "
                    + nullSafe(ticket.getTitle()), 128);
            String content = truncate(buildSlaContent(eventType, ticket, detail), 500);
            for (NotifyRule rule : rules) {
                if (!priorityMatches(rule.getConditionJson(), ticket.getPriority())) {
                    continue;
                }
                Set<Long> receivers = resolveReceivers(rule.getReceivers(), ticket.getAssigneeId(), tenantId);
                if (receivers.isEmpty()) {
                    continue;
                }
                boolean inApp = Boolean.TRUE.equals(rule.getInAppEnabled());
                boolean email = Boolean.TRUE.equals(rule.getEmailEnabled());
                if (inApp) {
                    List<Notification> saved = notificationService.sendToUsers(
                            receivers, tenantId, eventType, title, content,
                            "ticket", String.valueOf(ticket.getId()));
                    if (email) {
                        mailService.enqueue(saved);
                    }
                } else if (email) {
                    mailService.enqueueForUsers(receivers, title, content);
                }
                log.info("[通知] SLA 事件 {} 已分发：工单 #{} → {} 人（站内信={}, 邮件={}）",
                        eventType, ticket.getId(), receivers.size(), inApp, email);
            }
        } catch (Exception e) {
            log.error("[通知] SLA 事件 {} 分发失败（不影响主流程）：{}", eventType, e.getMessage(), e);
        }
    }

    /**
     * 解析规则中的接收人标识：ASSIGNEE / MANAGER / USER:{id} / ROLE:{role}，逗号分隔，去重。
     */
    private Set<Long> resolveReceivers(String receivers, Long assigneeId, Long tenantId) {
        Set<Long> ids = new LinkedHashSet<>();
        if (receivers == null) {
            return ids;
        }
        for (String raw : receivers.split(",")) {
            String token = raw.trim().toUpperCase();
            if (token.isEmpty()) {
                continue;
            }
            if (NotifyType.RECEIVER_ASSIGNEE.equals(token)) {
                if (assigneeId != null) {
                    ids.add(assigneeId);
                }
            } else if (NotifyType.RECEIVER_MANAGER.equals(token)) {
                addRoleUsers(ids, "MANAGER", tenantId);
            } else if (token.startsWith("USER:")) {
                try {
                    ids.add(Long.parseLong(token.substring("USER:".length())));
                } catch (NumberFormatException ignored) {
                    // 非法用户标识忽略
                }
            } else if (token.startsWith("ROLE:")) {
                addRoleUsers(ids, token.substring("ROLE:".length()), tenantId);
            }
        }
        return ids;
    }

    private void addRoleUsers(Set<Long> ids, String role, Long tenantId) {
        for (User u : userRepository.findByRoleIgnoreCase(role)) {
            if (tenantId == null || u.getTenantId() == null || tenantId.equals(u.getTenantId())) {
                ids.add(u.getId());
            }
        }
    }

    private static Long tenantOf(Ticket ticket) {
        return ticket.getTenant() == null ? null : ticket.getTenant().getId();
    }

    /** 解析 conditionJson 中的优先级条件（形如 {"priorities":["P1","P2"]}），空条件表示全部优先级。 */
    private boolean priorityMatches(String conditionJson, String priority) {
        if (conditionJson == null || conditionJson.isBlank()) {
            return true;
        }
        String json = conditionJson.replace(" ", "");
        int idx = json.indexOf("\"priorities\"");
        if (idx < 0) {
            idx = json.indexOf("\"priority\"");
        }
        if (idx < 0) {
            return true;
        }
        int colon = json.indexOf(':', idx);
        if (colon < 0) {
            return true;
        }
        int end = json.indexOf('}', colon);
        String seg = json.substring(colon + 1, end < 0 ? json.length() : end)
                .replace("[", "").replace("]", "").replace("\"", "");
        if (seg.isBlank() || "all".equalsIgnoreCase(seg)) {
            return true;
        }
        for (String p : seg.split(",")) {
            if (p.trim().equalsIgnoreCase(priority)) {
                return true;
            }
        }
        return false;
    }

    private String buildSlaContent(String eventType, Ticket ticket, String detail) {
        String no = "#" + ticket.getId();
        String title = nullSafe(ticket.getTitle());
        if (NotifyType.SLA_WARNING.equals(eventType)) {
            return "工单 " + no + "「" + title + "」即将超时" +
                    (detail == null || detail.isBlank() ? "，请尽快处理。" : "（" + detail + "）。");
        }
        return "工单 " + no + "「" + title + "」已超时" +
                (detail == null || detail.isBlank() ? "，请立即处理。" : "（" + detail + "）。");
    }

    private String buildContent(String eventType, Ticket ticket, String actorName) {
        String who = (actorName == null || actorName.isBlank()) ? "系统" : actorName;
        String no = "#" + ticket.getId();
        String title = nullSafe(ticket.getTitle());
        if (NotifyType.TICKET_ASSIGNED.equals(eventType)) {
            return who + " 将工单 " + no + "「" + title + "」分派给你，请及时处理。";
        }
        if (NotifyType.TICKET_TRANSFERRED.equals(eventType)) {
            return who + " 将工单 " + no + "「" + title + "」转派给你，请及时处理。";
        }
        return who + " 触发「" + NotifyType.label(eventType) + "」：" + no + "「" + title + "」。";
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
