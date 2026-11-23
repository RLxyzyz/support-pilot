package com.supportpilot.repository;

import com.supportpilot.entity.SimilarityConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SimilarityConfigRepository extends JpaRepository<SimilarityConfig, Long> {

    Optional<SimilarityConfig> findByTenantId(Long tenantId);
}
