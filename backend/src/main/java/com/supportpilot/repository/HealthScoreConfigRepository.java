package com.supportpilot.repository;

import com.supportpilot.entity.HealthScoreConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HealthScoreConfigRepository extends JpaRepository<HealthScoreConfig, Long> {

    Optional<HealthScoreConfig> findByTenantId(Long tenantId);
}
