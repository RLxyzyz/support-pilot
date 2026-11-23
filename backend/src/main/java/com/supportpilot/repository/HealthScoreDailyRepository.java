package com.supportpilot.repository;

import com.supportpilot.entity.HealthScoreDaily;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HealthScoreDailyRepository extends JpaRepository<HealthScoreDaily, Long> {

    Optional<HealthScoreDaily> findFirstByCustomerIdOrderByScoreDateDesc(Long customerId);

    List<HealthScoreDaily> findByCustomerIdAndScoreDateBetweenOrderByScoreDateAsc(
            Long customerId, LocalDate from, LocalDate to);

    Optional<HealthScoreDaily> findByCustomerIdAndScoreDate(Long customerId, LocalDate scoreDate);

    List<HealthScoreDaily> findByScoreDate(LocalDate scoreDate);
}
