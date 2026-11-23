package com.supportpilot.repository;

import com.supportpilot.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    List<Contact> findByCustomerIdOrderByIdDesc(Long customerId);

    List<Contact> findByCustomerIdAndIsPrimaryTrue(Long customerId);

    Optional<Contact> findByCustomerIdAndEmail(Long customerId, String email);

    /** 导入渠道按联系人邮箱反查（邮箱仅客户内唯一，可能多条）。 */
    List<Contact> findByEmailIgnoreCase(String email);

    /** GitHub 同步按作者登录名归户：同租户内按联系人姓名匹配。 */
    Optional<Contact> findFirstByTenantIdAndNameIgnoreCase(Long tenantId, String name);

    boolean existsByCustomerIdAndEmail(Long customerId, String email);

    boolean existsByCustomerIdAndEmailAndIdNot(Long customerId, String email, Long id);
}
