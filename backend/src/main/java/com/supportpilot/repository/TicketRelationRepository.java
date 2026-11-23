package com.supportpilot.repository;

import com.supportpilot.entity.TicketRelation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketRelationRepository extends JpaRepository<TicketRelation, Long> {

    /** 查询与某工单相关的所有关联记录（双向可见：ticket_id 或 related_ticket_id 等于该工单）。 */
    List<TicketRelation> findByTicketIdOrRelatedTicketId(Long ticketId, Long relatedTicketId);

    /** 判断两工单之间是否已存在关联记录（避免重复合并）。 */
    boolean existsByTicketIdAndRelatedTicketId(Long ticketId, Long relatedTicketId);
}
