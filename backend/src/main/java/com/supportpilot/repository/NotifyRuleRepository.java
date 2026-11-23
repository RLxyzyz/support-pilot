package com.supportpilot.repository;

import com.supportpilot.entity.NotifyRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotifyRuleRepository extends JpaRepository<NotifyRule, Long> {

    List<NotifyRule> findAllByOrderByIdAsc();

    List<NotifyRule> findByEventTypeAndEnabledTrue(String eventType);

    Optional<NotifyRule> findFirstByEventTypeAndEnabledTrue(String eventType);

    boolean existsByEventType(String eventType);
}
