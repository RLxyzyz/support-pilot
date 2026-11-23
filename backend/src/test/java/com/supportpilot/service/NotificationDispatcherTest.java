package com.supportpilot.service;

import com.supportpilot.entity.Notification;
import com.supportpilot.entity.NotifyRule;
import com.supportpilot.entity.Ticket;
import com.supportpilot.entity.User;
import com.supportpilot.repository.NotifyRuleRepository;
import com.supportpilot.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NotificationDispatcher：规则匹配、接收人解析、站内信/邮件通道开关（TR-10.1 / AC-N02b）。
 */
@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock
    NotifyRuleRepository notifyRuleRepository;
    @Mock
    NotificationService notificationService;
    @Mock
    MailService mailService;
    @Mock
    UserRepository userRepository;
    @InjectMocks
    NotificationDispatcher dispatcher;

    private Ticket ticket() {
        Ticket t = new Ticket();
        t.setId(41269L);
        t.setTitle("无法登录后台");
        return t;
    }

    private NotifyRule rule(boolean inApp, boolean email, String receivers) {
        NotifyRule r = new NotifyRule();
        r.setEventType("TICKET_ASSIGNED");
        r.setInAppEnabled(inApp);
        r.setEmailEnabled(email);
        r.setEnabled(true);
        r.setReceivers(receivers);
        return r;
    }

    @Test
    void assigneeAndManagerResolved_andEmailOffCreatesInAppOnly() {
        when(notifyRuleRepository.findByEventTypeAndEnabledTrue("TICKET_ASSIGNED"))
                .thenReturn(List.of(rule(true, false, "ASSIGNEE,MANAGER")));
        User manager = new User();
        manager.setId(5L);
        when(userRepository.findByRoleIgnoreCase("MANAGER")).thenReturn(List.of(manager));
        when(notificationService.sendToUsers(any(), isNull(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(new Notification()));

        dispatcher.dispatchTicketEvent("TICKET_ASSIGNED", ticket(), 7L, "管理员");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(notificationService).sendToUsers(captor.capture(), isNull(), eq("TICKET_ASSIGNED"), anyString(),
                anyString(), eq("ticket"), eq("41269"));
        assertEquals(2, captor.getValue().size());
        assertTrue(captor.getValue().containsAll(List.of(7L, 5L)));
        verify(mailService, never()).enqueue(any());
        verify(mailService, never()).enqueueForUsers(any(), anyString(), anyString());
    }

    @Test
    void emailOnInAppOff_sendsMailWithoutNotification() {
        when(notifyRuleRepository.findByEventTypeAndEnabledTrue("TICKET_ASSIGNED"))
                .thenReturn(List.of(rule(false, true, "ASSIGNEE")));

        dispatcher.dispatchTicketEvent("TICKET_ASSIGNED", ticket(), 7L, "管理员");

        verify(notificationService, never()).sendToUsers(any(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(mailService).enqueueForUsers(any(), anyString(), anyString());
    }

    @Test
    void noEnabledRule_doesNothing() {
        when(notifyRuleRepository.findByEventTypeAndEnabledTrue("TICKET_ASSIGNED")).thenReturn(List.of());

        dispatcher.dispatchTicketEvent("TICKET_ASSIGNED", ticket(), 7L, "管理员");

        verify(notificationService, never()).sendToUsers(any(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(mailService, never()).enqueue(any());
    }
}
