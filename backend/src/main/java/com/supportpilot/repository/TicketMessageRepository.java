package com.supportpilot.repository;

import com.supportpilot.entity.TicketMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketMessageRepository extends JpaRepository<TicketMessage, Long> {

    /** 判断该工单下是否已存在指定外部评论 id（用于 GitHub 评论增量同步去重）。 */
    boolean existsByTicketIdAndExternalId(Long ticketId, Long externalId);

    /** 按工单取全部消息（时间正序），供摘要 / 情绪统计使用。 */
    List<TicketMessage> findByTicketIdOrderByIdAsc(Long ticketId);
}
