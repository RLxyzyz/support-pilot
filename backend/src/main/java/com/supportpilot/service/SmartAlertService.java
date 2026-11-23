package com.supportpilot.service;

import com.supportpilot.common.Role;
import com.supportpilot.entity.Notification;
import com.supportpilot.entity.User;
import com.supportpilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * V2.3 智能告警通知：故障风暴 / 负面情绪 / 客户健康分。
 *
 * <p>不依赖 notify_rule 配置，直接向主管与指定处理人发送站内信；任何异常内部吞掉，
 * 绝不阻断主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartAlertService {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    /** 发送给主管（role=MANAGER），tenantId 为空表示全部租户。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyManagers(Long tenantId, String type, String title, String content,
                               String refType, String refId) {
        try {
            Set<Long> ids = new LinkedHashSet<>();
            for (User u : userRepository.findByRoleIgnoreCase(Role.MANAGER)) {
                if (tenantId == null || u.getTenantId() == null || tenantId.equals(u.getTenantId())) {
                    ids.add(u.getId());
                }
            }
            send(ids, tenantId, type, title, content, refType, refId);
        } catch (Exception e) {
            log.warn("[智能告警] {} 发送失败（不影响主流程）：{}", type, e.getMessage());
        }
    }

    /** 发送给指定用户（如处理人），可与主管合并。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyUsers(Collection<Long> userIds, Long tenantId, String type, String title,
                            String content, String refType, String refId) {
        try {
            send(userIds, tenantId, type, title, content, refType, refId);
        } catch (Exception e) {
            log.warn("[智能告警] {} 发送失败（不影响主流程）：{}", type, e.getMessage());
        }
    }

    private void send(Collection<Long> ids, Long tenantId, String type, String title,
                      String content, String refType, String refId) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<Notification> saved = notificationService.sendToUsers(
                ids, tenantId, type, truncate(title, 128), truncate(content, 500), refType, refId);
        if (!saved.isEmpty()) {
            log.info("[智能告警] {} 已发送 {} 条：{}", type, saved.size(), title);
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
