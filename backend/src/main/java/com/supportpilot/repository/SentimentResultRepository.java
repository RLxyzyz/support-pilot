package com.supportpilot.repository;

import com.supportpilot.entity.SentimentResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface SentimentResultRepository extends JpaRepository<SentimentResult, Long> {

    List<SentimentResult> findByTicketIdOrderByIdAsc(Long ticketId);

    long countByTicketIdAndSentiment(Long ticketId, String sentiment);

    List<SentimentResult> findByCreatedAtGreaterThanEqual(Instant since);

    List<SentimentResult> findByTicketIdIn(Collection<Long> ticketIds);
}
