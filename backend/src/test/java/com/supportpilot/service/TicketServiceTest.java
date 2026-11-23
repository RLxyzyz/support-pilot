package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.dto.CreateTicketRequest;
import com.supportpilot.repository.ContactRepository;
import com.supportpilot.repository.CustomerRepository;
import com.supportpilot.repository.ProductRepository;
import com.supportpilot.repository.TenantRepository;
import com.supportpilot.repository.TicketMessageRepository;
import com.supportpilot.repository.TicketRelationRepository;
import com.supportpilot.repository.TicketRepository;
import com.supportpilot.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Task 3 单测：创建工单未选客户时报「请选择所属客户」（AC-C03a / TR-3.5）。纯 Mockito。
 */
@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock TicketRepository ticketRepository;
    @Mock TenantRepository tenantRepository;
    @Mock ProductRepository productRepository;
    @Mock UserRepository userRepository;
    @Mock TicketMessageRepository ticketMessageRepository;
    @Mock TicketRelationRepository ticketRelationRepository;
    @Mock CustomerRepository customerRepository;
    @Mock ContactRepository contactRepository;
    @Mock KbService kbService;
    @Mock SlaService slaService;

    @InjectMocks TicketService ticketService;

    @Test
    void create_未选客户应报错且不落库() {
        CreateTicketRequest req = new CreateTicketRequest();
        req.setTitle("测试工单");
        // customerId 留空

        assertThatThrownBy(() -> ticketService.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请选择所属客户");

        verify(ticketRepository, never()).save(any());
    }
}
