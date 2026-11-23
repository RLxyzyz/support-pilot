package com.supportpilot.repository;

import com.supportpilot.entity.AiEvalRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiEvalRunRepository extends JpaRepository<AiEvalRun, Long> {

    Optional<AiEvalRun> findTopByOrderByIdDesc();
}
