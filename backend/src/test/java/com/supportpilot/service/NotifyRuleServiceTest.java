package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.dto.NotifyRuleRequest;
import com.supportpilot.entity.NotifyRule;
import com.supportpilot.repository.NotifyRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NotifyRuleService：默认规则幂等种子 + 事件类型校验（TR-9.1）。
 */
@ExtendWith(MockitoExtension.class)
class NotifyRuleServiceTest {

    @Mock
    NotifyRuleRepository notifyRuleRepository;
    @InjectMocks
    NotifyRuleService notifyRuleService;

    @Test
    void seedDefaults_insertsFourRulesWhenEmpty() {
        when(notifyRuleRepository.existsByEventType(anyString())).thenReturn(false);

        notifyRuleService.seedDefaults();

        ArgumentCaptor<NotifyRule> captor = ArgumentCaptor.forClass(NotifyRule.class);
        verify(notifyRuleRepository, times(4)).save(captor.capture());
        List<String> eventTypes = captor.getAllValues().stream().map(NotifyRule::getEventType).toList();
        assertEquals(List.of("TICKET_ASSIGNED", "TICKET_TRANSFERRED", "SLA_WARNING", "SLA_BREACH"), eventTypes);
    }

    @Test
    void seedDefaults_isIdempotentWhenRulesExist() {
        when(notifyRuleRepository.existsByEventType(anyString())).thenReturn(true);

        notifyRuleService.seedDefaults();

        verify(notifyRuleRepository, never()).save(any());
    }

    @Test
    void create_unknownEventType_rejected() {
        NotifyRuleRequest req = new NotifyRuleRequest();
        req.setEventType("NOT_A_EVENT");
        req.setReceivers("ASSIGNEE");

        BusinessException ex = assertThrows(BusinessException.class, () -> notifyRuleService.create(req));
        assertEquals(400, ex.getCode());
    }

    @Test
    void create_duplicateEventType_rejected() {
        NotifyRuleRequest req = new NotifyRuleRequest();
        req.setEventType("ticket_assigned");
        req.setReceivers("ASSIGNEE");
        when(notifyRuleRepository.existsByEventType("TICKET_ASSIGNED")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> notifyRuleService.create(req));
        assertEquals(400, ex.getCode());
    }
}
