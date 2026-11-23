package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.dto.MailTaskVO;
import com.supportpilot.entity.MailTask;
import com.supportpilot.entity.Notification;
import com.supportpilot.entity.User;
import com.supportpilot.repository.MailTaskRepository;
import com.supportpilot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MailService：SMTP 未配置时的优雅降级与人工重发约束（AC-N03b）。
 */
@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock
    MailTaskRepository mailTaskRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    ObjectProvider<JavaMailSender> mailSenderProvider;
    @InjectMocks
    MailService mailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mailService, "mailHost", "");
        ReflectionTestUtils.setField(mailService, "mailFrom", "no-reply@supportpilot.local");
        ReflectionTestUtils.setField(mailService, "mailDomain", "supportpilot.local");
    }

    @Test
    void isConfigured_falseWhenHostBlank() {
        assertFalse(mailService.isConfigured());
    }

    @Test
    void enqueue_withoutSmtp_marksTaskFailedAndDegrades() {
        User u = new User();
        u.setId(5L);
        u.setUsername("eng5");
        when(userRepository.findById(5L)).thenReturn(Optional.of(u));
        when(mailTaskRepository.save(any(MailTask.class))).thenAnswer(inv -> inv.getArgument(0));

        Notification n = new Notification();
        n.setId(11L);
        n.setUserId(5L);
        n.setTitle("【工单分派】#1 测试");
        n.setContent("分派给你");

        List<MailTask> tasks = mailService.enqueue(List.of(n));

        assertEquals(1, tasks.size());
        MailTask t = tasks.get(0);
        assertEquals("failed", t.getStatus());
        assertEquals(MailService.MAX_RETRY, t.getRetryCount());
        assertTrue(t.getLastError().contains("SMTP 未配置"));
        assertEquals("eng5@supportpilot.local", t.getToAddr());
    }

    @Test
    void resend_onlyAllowedForFailedTask() {
        MailTask sent = new MailTask();
        sent.setId(1L);
        sent.setStatus("sent");
        when(mailTaskRepository.findById(1L)).thenReturn(Optional.of(sent));

        BusinessException ex = assertThrows(BusinessException.class, () -> mailService.resend(1L));
        assertEquals(400, ex.getCode());
    }

    @Test
    void resend_failedTaskRetriesAndStaysFailedWithoutSmtp() {
        MailTask failed = new MailTask();
        failed.setId(2L);
        failed.setStatus("failed");
        failed.setRetryCount(3);
        failed.setToAddr("a@b.com");
        failed.setSubject("s");
        when(mailTaskRepository.findById(2L)).thenReturn(Optional.of(failed));
        when(mailTaskRepository.save(any(MailTask.class))).thenAnswer(inv -> inv.getArgument(0));

        MailTaskVO result = mailService.resend(2L);

        assertEquals("failed", result.getStatus());
        assertEquals(MailService.MAX_RETRY, result.getRetryCount());
    }

    /** 首次发送 + 重试 3 次（1/5/15 分钟）后仍失败 → 终态 failed；未到退避时间的任务跳过。 */
    @Test
    void processPending_thirdRetryFailsAndLeavesFailed() {
        ReflectionTestUtils.setField(mailService, "mailHost", "smtp.example.com");
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("smtp down")).when(sender).send(any(SimpleMailMessage.class));
        when(mailSenderProvider.getIfAvailable()).thenReturn(sender);

        MailTask due = pendingTask(7L, 3, Instant.now().minusSeconds(16 * 60));
        MailTask notDue = pendingTask(8L, 1, Instant.now());
        when(mailTaskRepository.findByStatusAndRetryCountLessThanEqual("pending", MailService.MAX_RETRY))
                .thenReturn(List.of(due, notDue));
        when(mailTaskRepository.save(any(MailTask.class))).thenAnswer(inv -> inv.getArgument(0));

        mailService.processPending();

        assertEquals("failed", due.getStatus());
        assertEquals(4, due.getRetryCount());
        assertEquals("pending", notDue.getStatus());
        assertEquals(1, notDue.getRetryCount());
    }

    private MailTask pendingTask(Long id, int retryCount, Instant updatedAt) {
        MailTask t = new MailTask();
        t.setId(id);
        t.setStatus("pending");
        t.setRetryCount(retryCount);
        t.setToAddr("a@b.com");
        t.setSubject("s");
        t.setCreatedAt(updatedAt);
        t.setUpdatedAt(updatedAt);
        return t;
    }
}
