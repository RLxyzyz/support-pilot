package com.supportpilot.repository;

import com.supportpilot.entity.AiEvalCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiEvalCaseRepository extends JpaRepository<AiEvalCase, Long> {

    List<AiEvalCase> findAllByOrderByIdDesc();

    List<AiEvalCase> findByTenantIdOrderByIdDesc(Long tenantId);

    long countByTenantId(Long tenantId);

    long countByStatus(String status);
}
