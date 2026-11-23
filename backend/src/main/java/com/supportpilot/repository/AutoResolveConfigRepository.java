package com.supportpilot.repository;

import com.supportpilot.entity.AutoResolveConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AutoResolveConfigRepository extends JpaRepository<AutoResolveConfig, Long> {

    List<AutoResolveConfig> findByTenantIdOrderByIdAsc(Long tenantId);

    Optional<AutoResolveConfig> findByTenantIdAndCategory(Long tenantId, String category);
}
