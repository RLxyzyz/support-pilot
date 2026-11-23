package com.supportpilot.repository;

import com.supportpilot.entity.TicketSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TicketSummaryRepository extends JpaRepository<TicketSummary, Long> {

    Optional<TicketSummary> findByTicketId(Long ticketId);

    void deleteByTicketId(Long ticketId);
}
