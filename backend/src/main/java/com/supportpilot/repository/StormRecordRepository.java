package com.supportpilot.repository;

import com.supportpilot.entity.StormRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StormRecordRepository extends JpaRepository<StormRecord, Long> {

    List<StormRecord> findByStatusOrderByIdDesc(String status);

    Optional<StormRecord> findFirstByStormKeyAndStatus(String stormKey, String status);

    List<StormRecord> findByStatus(String status);
}
