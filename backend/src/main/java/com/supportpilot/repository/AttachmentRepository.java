package com.supportpilot.repository;

import com.supportpilot.entity.Attachment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByTicketIdAndDeletedFalseAndPurgedFalseOrderByIdAsc(Long ticketId);

    List<Attachment> findByMessageIdInAndDeletedFalseAndPurgedFalseOrderByIdAsc(Collection<Long> messageIds);

    Optional<Attachment> findFirstByTicketIdAndSha256AndDeletedFalseAndPurgedFalse(Long ticketId, String sha256);

    Optional<Attachment> findByIdAndDeletedFalse(Long id);

    long countByTicketIdAndDeletedFalseAndPurgedFalse(Long ticketId);

    @Query("select coalesce(sum(a.sizeBytes), 0) from Attachment a " +
            "where a.ticketId = :ticketId and a.deleted = false and a.purged = false")
    long sumSizeByTicket(@Param("ticketId") Long ticketId);

    @Query("select coalesce(sum(a.sizeBytes), 0) from Attachment a where a.purged = false")
    long sumAllSize();

    /** 清理候选：工单已解决且解决时间早于 cutoff。 */
    @Query("select a from Attachment a, Ticket t where a.ticketId = t.id " +
            "and a.purged = false and t.resolvedAt is not null and t.resolvedAt < :cutoff " +
            "order by a.id asc")
    List<Attachment> findPurgeCandidates(@Param("cutoff") Instant cutoff, Pageable pageable);

    @Query("select count(a) from Attachment a, Ticket t where a.ticketId = t.id " +
            "and a.purged = false and t.resolvedAt is not null and t.resolvedAt < :cutoff")
    long countPurgeCandidates(@Param("cutoff") Instant cutoff);
}
