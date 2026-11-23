package com.supportpilot.repository;

import com.supportpilot.entity.TicketSimilarity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketSimilarityRepository extends JpaRepository<TicketSimilarity, Long> {

    List<TicketSimilarity> findByTicketId(Long ticketId);

    List<TicketSimilarity> findByGroupId(Long groupId);

    Optional<TicketSimilarity> findFirstByTicketIdAndGroupIdIsNotNull(Long ticketId);

    boolean existsByTicketIdAndSimilarTicketId(Long ticketId, Long similarTicketId);

    List<TicketSimilarity> findBySimilarTicketId(Long similarTicketId);

    /** 组内相似关系数量（用于列表徽标）。 */
    long countByGroupId(Long groupId);
}
