package com.supportpilot.repository;

import com.supportpilot.entity.EvalCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EvalCaseRepository extends JpaRepository<EvalCase, Long> {

    List<EvalCase> findAllByOrderByWeightDesc();

    /** 按租户读取知识盲区（tid 为 null 时返回全局种子数据）。 */
    @Query("select e from EvalCase e where (:tid is null and e.tenantId is null) or e.tenantId = :tid " +
            "order by e.weight desc")
    List<EvalCase> findMisses(@Param("tid") Long tid);

    /** 按问题 + 租户定位（用于 RAG 未命中 upsert；tid 为 null 时匹配全局行）。 */
    @Query("select e from EvalCase e where e.question = :q and " +
            "((:tid is null and e.tenantId is null) or e.tenantId = :tid)")
    Optional<EvalCase> findByQuestionAndTenant(@Param("q") String question, @Param("tid") Long tenantId);
}
