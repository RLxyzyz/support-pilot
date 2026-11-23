package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.dto.ContactRequest;
import com.supportpilot.dto.ContactVO;
import com.supportpilot.entity.Contact;
import com.supportpilot.entity.Customer;
import com.supportpilot.repository.ContactRepository;
import com.supportpilot.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 客户联系人服务（V2.0 EP1）：客户下联系人 CRUD。
 * 业务规则：同客户内 email 唯一（AC-C02b）；每客户仅一个主联系人，设置新主联系人时
 * 自动取消旧主联系人（AC-C02a），在同一事务内完成。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContactService {

    private final ContactRepository contactRepository;
    private final CustomerRepository customerRepository;

    @Value("${app.tenant.default-id:1}")
    private Long defaultTenantId;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    public List<ContactVO> list(Long customerId) {
        loadCustomer(customerId);
        return contactRepository.findByCustomerIdOrderByIdDesc(customerId).stream()
                .map(ContactService::toVO)
                .toList();
    }

    @Transactional
    public ContactVO create(Long customerId, ContactRequest req) {
        Customer c = loadCustomer(customerId);
        String email = normalizeEmail(req.getEmail());
        validateEmailUnique(customerId, null, email);
        Contact contact = new Contact();
        contact.setCustomerId(customerId);
        contact.setTenantId(c.getTenantId() != null ? c.getTenantId() : defaultTenantId);
        contact.setName(req.getName().trim());
        contact.setEmail(email);
        contact.setPhone(req.getPhone());
        contact.setTitle(req.getTitle());
        boolean primary = Boolean.TRUE.equals(req.getIsPrimary());
        contact.setIsPrimary(primary);
        contact.setCreatedAt(Instant.now());
        if (primary) {
            clearOtherPrimaries(customerId, null);
        }
        return toVO(contactRepository.save(contact));
    }

    @Transactional
    public ContactVO update(Long customerId, Long id, ContactRequest req) {
        loadCustomer(customerId);
        Contact contact = load(id);
        if (!customerId.equals(contact.getCustomerId())) {
            throw BusinessException.badRequest("联系人不属于该客户");
        }
        String email = normalizeEmail(req.getEmail());
        validateEmailUnique(customerId, id, email);
        contact.setName(req.getName().trim());
        contact.setEmail(email);
        contact.setPhone(req.getPhone());
        contact.setTitle(req.getTitle());
        if (req.getIsPrimary() != null) {
            boolean primary = Boolean.TRUE.equals(req.getIsPrimary());
            if (primary) {
                clearOtherPrimaries(customerId, id);
            }
            contact.setIsPrimary(primary);
        }
        return toVO(contactRepository.save(contact));
    }

    /** 将指定联系人设为主联系人，同事务内取消该客户旧主联系人。 */
    @Transactional
    public ContactVO setPrimary(Long customerId, Long id) {
        loadCustomer(customerId);
        Contact contact = load(id);
        if (!customerId.equals(contact.getCustomerId())) {
            throw BusinessException.badRequest("联系人不属于该客户");
        }
        clearOtherPrimaries(customerId, id);
        contact.setIsPrimary(true);
        return toVO(contactRepository.save(contact));
    }

    @Transactional
    public void delete(Long customerId, Long id) {
        loadCustomer(customerId);
        Contact contact = load(id);
        if (!customerId.equals(contact.getCustomerId())) {
            throw BusinessException.badRequest("联系人不属于该客户");
        }
        contactRepository.delete(contact);
    }

    // ===== 内部方法 =====

    private Customer loadCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> BusinessException.notFound("客户不存在"));
    }

    private Contact load(Long id) {
        return contactRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("联系人不存在"));
    }

    /** 同客户内 email 唯一校验（excludeId 为 null 表示新建场景）。 */
    private void validateEmailUnique(Long customerId, Long excludeId, String email) {
        if (email == null) {
            return;
        }
        boolean exists = excludeId == null
                ? contactRepository.existsByCustomerIdAndEmail(customerId, email)
                : contactRepository.existsByCustomerIdAndEmailAndIdNot(customerId, email, excludeId);
        if (exists) {
            throw BusinessException.badRequest("该客户下已存在相同邮箱的联系人");
        }
    }

    /** 取消该客户下除 excludeId 外所有主联系人标记（事务内）。 */
    private void clearOtherPrimaries(Long customerId, Long excludeId) {
        List<Contact> primaries = contactRepository.findByCustomerIdAndIsPrimaryTrue(customerId);
        for (Contact p : primaries) {
            if (excludeId == null || !excludeId.equals(p.getId())) {
                p.setIsPrimary(false);
                contactRepository.save(p);
            }
        }
    }

    private static String normalizeEmail(String email) {
        return (email == null || email.isBlank()) ? null : email.trim();
    }

    private static ContactVO toVO(Contact c) {
        ContactVO vo = new ContactVO();
        vo.setId(c.getId());
        vo.setCustomerId(c.getCustomerId());
        vo.setName(c.getName());
        vo.setEmail(c.getEmail());
        vo.setPhone(c.getPhone());
        vo.setTitle(c.getTitle());
        vo.setIsPrimary(c.getIsPrimary());
        vo.setCreatedAt(c.getCreatedAt() == null ? null : TIME_FMT.format(c.getCreatedAt()));
        return vo;
    }
}
