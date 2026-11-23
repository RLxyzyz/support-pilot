package com.supportpilot.repository;

import com.supportpilot.entity.ReplyTemplate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReplyTemplateRepository extends JpaRepository<ReplyTemplate, Long> {

    List<ReplyTemplate> findAllByOrderByIdDesc();

    List<ReplyTemplate> findByTenantIdOrderByIdDesc(Long tenantId);

    List<ReplyTemplate> findByTenantId(Long tenantId, Sort sort);
}
