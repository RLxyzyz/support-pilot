package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.dto.MailTaskVO;
import com.supportpilot.entity.MailTask;
import com.supportpilot.entity.Notification;
import com.supportpilot.entity.User;
import com.supportpilot.repository.MailTaskRepository;
import com.supportpilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 邮件通道（V2.0 EP3）：异步轮询发送 + 失败重试（1 / 5 / 15 分钟）+ 人工重发。
 *
 * <p>降级策略：`spring.mail.host` 为空（SMTP 未配置）时，邮件任务直接标记为 failed、
 * retry_count=3，不做无意义的等待重试；站内信不受影响，主流程永不因邮件失败而中断。
 *
 * <p>凭据仅通过环境变量注入，日志中不输出密码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    /** 最大失败次数（达到后标记 failed，可人工重发）。 */
    public static final int MAX_RETRY = 3;

    /** 第 N 次重试前的等待分钟数：首次立即，之后 1 / 5 / 15 分钟。 */
    private static final int[] BACKOFF_MINUTES = {0, 1, 5, 15};

    private final MailTaskRepository mailTaskRepository;
    private final UserRepository userRepository;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${app.notify.mail-from:no-reply@supportpilot.local}")
    private String mailFrom;

    @Value("${app.notify.mail-domain:supportpilot.local}")
    private String mailDomain;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    /** SMTP 是否已配置。 */
    public boolean isConfigured() {
        return mailHost != null && !mailHost.isBlank();
    }

    /** 依据已落库的站内信生成邮件任务（收件人取用户 email，为空回退「登录名@mail-domain」）。 */
    public List<MailTask> enqueue(List<Notification> notifications) {
        List<MailTask> tasks = new ArrayList<>();
        for (Notification n : notifications) {
            if (n.getUserId() == null) {
                continue;
            }
            User user = userRepository.findById(n.getUserId()).orElse(null);
            if (user == null) {
                continue;
            }
            tasks.add(createTask(resolveToAddr(user), n.getId(), n.getTitle(), n.getContent()));
        }
        return tasks;
    }

    /** 站内信关闭但邮件开启时，直接按用户生成邮件任务。 */
    public List<MailTask> enqueueForUsers(Collection<Long> userIds, String subject, String content) {
        List<MailTask> tasks = new ArrayList<>();
        for (Long uid : userIds) {
            User user = userRepository.findById(uid).orElse(null);
            if (user != null) {
                tasks.add(createTask(resolveToAddr(user), null, subject, content));
            }
        }
        return tasks;
    }

    /** 直接向任意邮箱生成邮件任务（如回复客户联系人），不依赖系统用户。 */
    public MailTask enqueueExternal(String toAddr, String subject, String content) {
        if (toAddr == null || toAddr.isBlank()) {
            return null;
        }
        return createTask(toAddr.trim(), null, subject, content);
    }

    public List<MailTaskVO> list(String status) {
        List<MailTask> tasks = (status == null || status.isBlank())
                ? mailTaskRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt", "id"))
                : mailTaskRepository.findByStatusOrderByIdDesc(status);
        return tasks.stream().map(this::toVO).toList();
    }

    /** 人工重发：仅失败任务可重发，重置重试计数后立即尝试一次。 */
    public MailTaskVO resend(Long id) {
        MailTask task = mailTaskRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("邮件任务不存在"));
        if (!"failed".equals(task.getStatus())) {
            throw BusinessException.badRequest("仅失败状态的邮件任务可以重发");
        }
        task.setStatus("pending");
        task.setRetryCount(0);
        task.setLastError(null);
        task.setUpdatedAt(Instant.now());
        attempt(task);
        return toVO(mailTaskRepository.save(task));
    }

    /** 每 20 秒轮询一次待发送任务（异步，不阻塞业务接口）；按退避策略控制重试节奏。 */
    @Scheduled(fixedDelay = 20_000, initialDelay = 15_000)
    public void processPending() {
        List<MailTask> pending = mailTaskRepository.findByStatusAndRetryCountLessThanEqual("pending", MAX_RETRY);
        Instant now = Instant.now();
        for (MailTask task : pending) {
            if (!isDue(task, now)) {
                continue;
            }
            try {
                attempt(task);
                mailTaskRepository.save(task);
            } catch (Exception e) {
                log.warn("[邮件] 处理任务 #{} 异常：{}", task.getId(), e.getMessage());
            }
        }
    }

    private void attempt(MailTask task) {
        task.setUpdatedAt(Instant.now());
        if (!isConfigured()) {
            // 优雅降级：不等待重试，直接置失败并给出明确原因
            task.setStatus("failed");
            task.setRetryCount(MAX_RETRY);
            task.setLastError("SMTP 未配置，邮件已降级为仅站内信");
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            task.setStatus("failed");
            task.setRetryCount(MAX_RETRY);
            task.setLastError("邮件发送器不可用（缺少 JavaMailSender）");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(mailFrom);
            msg.setTo(task.getToAddr());
            msg.setSubject(task.getSubject());
            msg.setText(task.getContent() == null ? "" : task.getContent());
            sender.send(msg);
            task.setStatus("sent");
            task.setLastError(null);
        } catch (Exception e) {
            int retry = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
            task.setRetryCount(retry);
            task.setLastError(truncate(e.getMessage(), 500));
            if (retry > MAX_RETRY) {
                // 首次发送 + 重试 3 次（1 / 5 / 15 分钟）后仍失败 → 终态 failed，可人工重发
                task.setStatus("failed");
                log.warn("[邮件] 任务 #{} 重试 {} 次后仍失败：{}", task.getId(), MAX_RETRY, e.getMessage());
            } else {
                log.info("[邮件] 任务 #{} 第 {} 次发送失败，将按退避策略重试：{}", task.getId(), retry, e.getMessage());
            }
        }
    }

    private boolean isDue(MailTask task, Instant now) {
        Instant base = task.getUpdatedAt() == null ? task.getCreatedAt() : task.getUpdatedAt();
        if (base == null) {
            return true;
        }
        int retry = task.getRetryCount() == null ? 0 : task.getRetryCount();
        int minutes = BACKOFF_MINUTES[Math.min(retry, BACKOFF_MINUTES.length - 1)];
        return !now.isBefore(base.plus(Duration.ofMinutes(minutes)));
    }

    private MailTask createTask(String toAddr, Long notificationId, String subject, String content) {
        MailTask task = new MailTask();
        task.setNotificationId(notificationId);
        task.setToAddr(toAddr);
        task.setSubject(truncate(subject == null ? "SupportPilot 通知" : subject, 250));
        task.setContent(content);
        task.setStatus("pending");
        task.setRetryCount(0);
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        if (!isConfigured()) {
            // 未配置 SMTP：入库即失败，管理端可见且可重发
            task.setStatus("failed");
            task.setRetryCount(MAX_RETRY);
            task.setLastError("SMTP 未配置，邮件已降级为仅站内信");
        }
        return mailTaskRepository.save(task);
    }

    /** 收件地址：优先用户表 email；未填写时按企业邮箱惯例用「登录名@mail-domain」推导。 */
    private String resolveToAddr(User user) {
        String email = user.getEmail();
        if (email != null && !email.isBlank()) {
            return email.trim();
        }
        return user.getUsername() + "@" + mailDomain;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private MailTaskVO toVO(MailTask t) {
        MailTaskVO v = new MailTaskVO();
        v.setId(t.getId());
        v.setNotificationId(t.getNotificationId());
        v.setToAddr(t.getToAddr());
        v.setSubject(t.getSubject());
        v.setStatus(t.getStatus());
        v.setRetryCount(t.getRetryCount());
        v.setLastError(t.getLastError());
        v.setCreatedAt(t.getCreatedAt() == null ? null : TIME_FMT.format(t.getCreatedAt()));
        v.setUpdatedAt(t.getUpdatedAt() == null ? null : TIME_FMT.format(t.getUpdatedAt()));
        return v;
    }
}
