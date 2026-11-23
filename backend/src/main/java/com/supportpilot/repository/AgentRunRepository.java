package com.supportpilot.repository;

import com.supportpilot.entity.AgentRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AgentRunRepository extends JpaRepository<AgentRun, Long> {

    /** 统计时间范围内的 token 总用量（按工单所属租户过滤），用于 AI 成本估算。 */
    @Query(value = "select coalesce(sum(ar.tokens), 0) from agent_run ar " +
            "left join ticket t on t.id = ar.ticket_id " +
            "where ar.created_at >= :since and ar.created_at < :until " +
            "and (:tid is null or t.tenant_id = :tid)", nativeQuery = true)
    long sumTokensInRange(@Param("since") Instant since,
                          @Param("until") Instant until,
                          @Param("tid") Long tid);
}
