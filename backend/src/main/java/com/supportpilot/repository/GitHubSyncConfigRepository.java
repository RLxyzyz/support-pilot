package com.supportpilot.repository;

import com.supportpilot.entity.GitHubSyncConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GitHubSyncConfigRepository extends JpaRepository<GitHubSyncConfig, Long> {

    Optional<GitHubSyncConfig> findFirstByOrderByIdAsc();
}
