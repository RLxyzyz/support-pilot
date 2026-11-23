package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.dto.ContactRequest;
import com.supportpilot.entity.Contact;
import com.supportpilot.entity.Customer;
import com.supportpilot.repository.ContactRepository;
import com.supportpilot.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Task 3 单测：主联系人切换自动取消旧主联系人（AC-C02a / TR-3.3）、
 * 同客户邮箱唯一拦截（AC-C02b / TR-3.4）。纯 Mockito。
 */
@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock ContactRepository contactRepository;
    @Mock CustomerRepository customerRepository;

    @InjectMocks ContactService contactService;

    @Test
    void create_设主联系人应在同事务内取消旧主联系人() {
        Customer c = new Customer();
        c.setId(1L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(c));

        Contact oldPrimary = new Contact();
        oldPrimary.setId(10L);
        oldPrimary.setCustomerId(1L);
        oldPrimary.setIsPrimary(true);
        when(contactRepository.findByCustomerIdAndIsPrimaryTrue(1L)).thenReturn(List.of(oldPrimary));

        ContactRequest req = new ContactRequest();
        req.setName("Y");
        req.setIsPrimary(true);

        // 生产环境 save 返回落库实体；mock 默认返回 null，这里让其回返回参以便 toVO 不 NPE
        when(contactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        contactService.create(1L, req);

        // 两次 save：先把旧主联系人置 false，再保存新主联系人
        ArgumentCaptor<Contact> cap = ArgumentCaptor.forClass(Contact.class);
        verify(contactRepository, times(2)).save(cap.capture());
        List<Contact> saved = cap.getAllValues();

        boolean oldCleared = saved.stream()
                .anyMatch(s -> Long.valueOf(10L).equals(s.getId()) && !Boolean.TRUE.equals(s.getIsPrimary()));
        boolean newIsPrimary = saved.stream()
                .anyMatch(s -> s.getId() == null && Boolean.TRUE.equals(s.getIsPrimary()));
        assertThat(oldCleared).as("旧主联系人应被取消").isTrue();
        assertThat(newIsPrimary).as("新联系人应为主联系人").isTrue();
    }

    @Test
    void create_同客户重复邮箱应拒绝且不落库() {
        Customer c = new Customer();
        c.setId(1L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(c));
        when(contactRepository.existsByCustomerIdAndEmail(1L, "a@b.com")).thenReturn(true);

        ContactRequest req = new ContactRequest();
        req.setName("X");
        req.setEmail("a@b.com");

        assertThatThrownBy(() -> contactService.create(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("邮箱");

        verify(contactRepository, never()).save(any());
    }
}
