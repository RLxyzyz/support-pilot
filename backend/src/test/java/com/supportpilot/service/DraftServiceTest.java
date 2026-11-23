package com.supportpilot.service;

import com.supportpilot.common.UserContext;
import com.supportpilot.entity.Draft;
import com.supportpilot.entity.Ticket;
import com.supportpilot.entity.TicketMessage;
import com.supportpilot.repository.DraftRepository;
import com.supportpilot.repository.KbDocRepository;
import com.supportpilot.repository.TicketMessageRepository;
import com.supportpilot.repository.TicketRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-4 冒烟测试：草稿采纳后必须把回复写入 ticket_message，并把最终采纳内容回写 draft，
 * 避免「状态已解决但会话里没有回复」的数据断裂。纯 Mockito，不依赖 DB / Spring 上下文。
 */
@ExtendWith(MockitoExtension.class)
class DraftServiceTest {

    @Mock TicketRepository ticketRepository;
    @Mock DraftRepository draftRepository;
    @Mock TicketMessageRepository ticketMessageRepository;
    @Mock KbDocRepository kbDocRepository;
    @Mock KbService kbService;
    @Mock SlaService slaService;

    @InjectMocks DraftService draftService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void approve_应写入回复消息并回写草稿内容() {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        Draft draft = new Draft();
        when(draftRepository.findFirstByTicketIdOrderByIdDesc(1L)).thenReturn(Optional.of(draft));

        Map<String, Object> resp = draftService.approve("d_1", "  已为您处理完毕  ");

        assertThat(resp).containsEntry("adopted", true);

        // 1) 采纳即回复：写入一条对客户可见（internal=false）的消息，内容为 trim 后的采纳回复
        ArgumentCaptor<TicketMessage> msgCap = ArgumentCaptor.forClass(TicketMessage.class);
        verify(ticketMessageRepository).save(msgCap.capture());
        TicketMessage msg = msgCap.getValue();
        assertThat(msg.getText()).isEqualTo("已为您处理完毕");
        assertThat(msg.getInternal()).isFalse();
        assertThat(msg.getTicket()).isSameAs(ticket);

        // 2) 草稿内容被更新为最终采纳回复，状态置为 adopted（供审计回溯）
        ArgumentCaptor<Draft> draftCap = ArgumentCaptor.forClass(Draft.class);
        verify(draftRepository).save(draftCap.capture());
        Draft saved = draftCap.getValue();
        assertThat(saved.getContent()).isEqualTo("已为您处理完毕");
        assertThat(saved.getStatus()).isEqualTo("adopted");

        // 3) 工单状态持久化为已解决
        assertThat(ticket.getStatus()).isEqualTo("已解决");
        verify(ticketRepository).save(ticket);
    }
}
