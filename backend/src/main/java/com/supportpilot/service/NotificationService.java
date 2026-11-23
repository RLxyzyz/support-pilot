package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.common.NotifyType;
import com.supportpilot.common.UserContext;
import com.supportpilot.dto.NotificationPageVO;
import com.supportpilot.dto.NotificationVO;
import com.supportpilot.entity.Notification;
import com.supportpilot.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 站内信服务（V2.0 EP3）：写入、未读数、分页列表、标记已读、90 天清理。
 *
 * <p>所有读取类接口只作用于当前登录用户；写入由事件分发器调用（Task 10）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /** 通知保留天数。 */
    private static final int RETENTION_DAYS = 90;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    /**
     * 批量写入站内信（去重用户）。返回实际写入的记录，供邮件通道复用。
     */
    @Transactional
    public List<Notification> sendToUsers(Collection<Long> userIds, String type, String title,
                                          String content, String refType, String refId) {
        return sendToUsers(userIds, null, type, title, content, refType, refId);
    }

    /** 带租户的写入（定时任务等无租户上下文场景，显式传入租户）。 */
    @Transactional
    public List<Notification> sendToUsers(Collection<Long> userIds, Long tenantId, String type, String title,
                                          String content, String refType, String refId) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>(userIds);
        distinct.remove(null);
        List<Notification> saved = new ArrayList<>(distinct.size());
        for (Long uid : distinct) {
            Notification n = new Notification();
            n.setTenantId(tenantId);
            n.setUserId(uid);
            n.setType(type);
            n.setTitle(title);
            n.setContent(content);
            n.setRefType(refType);
            n.setRefId(refId);
            n.setIsRead(false);
            n.setCreatedAt(Instant.now());
            saved.add(notificationRepository.save(n));
        }
        return saved;
    }

    public long unreadCount() {
        return notificationRepository.countByUserIdAndIsReadFalse(currentUserId());
    }

    public NotificationPageVO page(String type, int page, int size) {
        Long uid = currentUserId();
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100));
        Page<Notification> result = (type == null || type.isBlank())
                ? notificationRepository.findByUserIdOrderByCreatedAtDesc(uid, pageable)
                : notificationRepository.findByUserIdAndTypeOrderByCreatedAtDesc(uid, type, pageable);
        List<NotificationVO> list = result.getContent().stream().map(this::toVO).toList();
        return new NotificationPageVO(list, result.getTotalElements());
    }

    @Transactional
    public void markRead(Long id) {
        Long uid = currentUserId();
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("通知不存在"));
        if (!uid.equals(n.getUserId())) {
            throw new BusinessException(403, "无权限操作该通知");
        }
        if (!Boolean.TRUE.equals(n.getIsRead())) {
            n.setIsRead(true);
            n.setReadAt(Instant.now());
            notificationRepository.save(n);
        }
    }

    @Transactional
    public int markAllRead() {
        return notificationRepository.markAllRead(currentUserId(), Instant.now());
    }

    /** 每日 03:00 清理 90 天前的历史通知，避免表无限增长。 */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupExpired() {
        Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        long deleted = notificationRepository.deleteByCreatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("[通知清理] 已删除 {} 条 {} 天前的历史通知", deleted, RETENTION_DAYS);
        }
    }

    private Long currentUserId() {
        UserContext.CurrentUser u = UserContext.get();
        if (u == null) {
            throw new BusinessException(401, "未登录");
        }
        return u.getId();
    }

    private NotificationVO toVO(Notification n) {
        NotificationVO v = new NotificationVO();
        v.setId(n.getId());
        v.setType(n.getType());
        v.setTypeLabel(NotifyType.label(n.getType()));
        v.setTitle(n.getTitle());
        v.setContent(n.getContent());
        v.setRefType(n.getRefType());
        v.setRefId(n.getRefId());
        v.setIsRead(n.getIsRead());
        v.setReadAt(n.getReadAt() == null ? null : TIME_FMT.format(n.getReadAt()));
        v.setCreatedAt(n.getCreatedAt() == null ? null : TIME_FMT.format(n.getCreatedAt()));
        return v;
    }
}
