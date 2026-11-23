package com.supportpilot.repository;

import com.supportpilot.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findByTenantIdOrderByIdDesc(Long tenantId);

    Page<Customer> findByTenantId(Long tenantId, Pageable pageable);

    Optional<Customer> findByTenantIdAndCode(Long tenantId, String code);

    boolean existsByTenantIdAndCode(Long tenantId, String code);

    boolean existsByTenantIdAndCodeAndIdNot(Long tenantId, String code, Long id);

    /** 导入渠道按客户名称匹配（名称不唯一，取同租户首条）。 */
    List<Customer> findByNameIgnoreCase(String name);

    /**
     * 客户列表搜索：按租户（tid 为 null 表示全部）+ 服务等级 + 名称/编码关键字过滤。
     * 三个参数均为 null 时返回全部。
     */
    @Query("select c from Customer c where (:tid is null or c.tenantId = :tid) " +
            "and (:slaTier is null or c.slaTier = :slaTier) " +
            "and (:keyword is null or lower(c.name) like lower(concat('%', :keyword, '%')) " +
            "     or lower(c.code) like lower(concat('%', :keyword, '%')))")
    Page<Customer> search(@Param("tid") Long tid, @Param("slaTier") String slaTier,
                          @Param("keyword") String keyword, Pageable pageable);
}
