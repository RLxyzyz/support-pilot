package com.supportpilot.repository;

import com.supportpilot.entity.SlaCalendar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SlaCalendarRepository extends JpaRepository<SlaCalendar, Long> {

    List<SlaCalendar> findByTenantIdOrderByIdAsc(Long tenantId);

    Optional<SlaCalendar> findByIdAndTenantId(Long id, Long tenantId);
}
