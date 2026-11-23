package com.supportpilot.repository;

import com.supportpilot.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByTenantIdOrderByIdDesc(Long tenantId);

    List<Team> findAllByOrderByIdAsc();
}
