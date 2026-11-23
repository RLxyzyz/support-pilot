package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.dto.CustomerRequest;
import com.supportpilot.entity.Customer;
import com.supportpilot.repository.ContactRepository;
import com.supportpilot.repository.CustomerRepository;
import com.supportpilot.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Task 3 单测：客户编码重复拦截（AC-C01a / TR-3.1）、关联工单客户禁止删除（AC-C01b / TR-3.2）。
 * 纯 Mockito，不依赖 DB / Spring 上下文。
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock CustomerRepository customerRepository;
    @Mock ContactRepository contactRepository;
    @Mock TicketRepository ticketRepository;
    @Mock TicketService ticketService;
    @Mock SlaService slaService;

    @InjectMocks CustomerService customerService;

    @Test
    void create_重复code应拒绝且不落库() {
        CustomerRequest req = new CustomerRequest();
        req.setName("客户A");
        req.setCode("C001");
        when(customerRepository.existsByTenantIdAndCode(any(), eq("C001"))).thenReturn(true);

        assertThatThrownBy(() -> customerService.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户编码已存在");

        verify(customerRepository, never()).save(any());
    }

    @Test
    void delete_有关联工单应拒绝且不删除() {
        Customer c = new Customer();
        c.setId(10L);
        when(customerRepository.findById(10L)).thenReturn(Optional.of(c));
        when(ticketRepository.countByCustomerId(10L)).thenReturn(3L);

        assertThatThrownBy(() -> customerService.delete(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("关联工单");

        verify(customerRepository, never()).delete(any());
    }
}
