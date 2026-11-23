package com.supportpilot.repository;

import com.supportpilot.entity.SlaPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {

    List<SlaPolicy> findByTenantIdOrderByTierAscPriorityAsc(Long tenantId);

    Optional<SlaPolicy> findFirstByTenantIdAndTierAndPriorityAndEnabledTrue(Long tenantId, String tier, String priority);

    Optional<SlaPolicy> findFirstByTenantIdAndTierAndPriority(Long tenantId, String tier, String priority);

    long countByTenantIdAndCalendarId(Long tenantId, Long calendarId);
}
