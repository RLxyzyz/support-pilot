package com.supportpilot.repository;

import com.supportpilot.entity.Draft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DraftRepository extends JpaRepository<Draft, Long> {

    Optional<Draft> findFirstByTicketIdOrderByIdDesc(Long ticketId);

    /** 按状态统计草稿数（adopted / rejected），用于看板采纳率。 */
    long countByStatus(String status);

    /** 统计指定时间范围内某状态的草稿数，用于 AI 节省时长估算。 */
    long countByStatusAndCreatedAtBetween(String status, Instant start, Instant end);

    /** 范围内草稿总数（用于 RAG 命中率分母）。 */
    long countByCreatedAtBetween(Instant start, Instant end);

    /** 范围内带引用（命中过知识库）的草稿数（RAG 命中率分子）。 */
    @Query("select count(d) from Draft d where d.createdAt between :start and :end " +
            "and d.citedDocIds is not null and d.citedDocIds <> ''")
    long countWithCitesInRange(@Param("start") Instant start, @Param("end") Instant end);

    /** 驳回原因分布：返回 [feedback_reason, count]，null 原因归入「未填写」。 */
    @Query("select coalesce(d.feedbackReason, '未填写'), count(d) from Draft d " +
            "where d.status = 'rejected' group by d.feedbackReason order by count(d) desc")
    List<Object[]> rejectReasonDist();

    /** 采纳后被人工编辑过的草稿数（edited_content 非空）。 */
    @Query("select count(d) from Draft d where d.status = 'adopted' " +
            "and d.editedContent is not null and d.editedContent <> ''")
    long countEdited();
}
