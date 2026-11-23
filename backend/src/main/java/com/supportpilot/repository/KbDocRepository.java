package com.supportpilot.repository;

import com.supportpilot.entity.KbDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KbDocRepository extends JpaRepository<KbDoc, Long> {

    /** 采纳草稿时回填命中文档的命中次数。 */
    @Modifying
    @Query("update KbDoc d set d.hits = coalesce(d.hits, 0) + 1 where d.id = :id")
    void incrementHits(@Param("id") Long id);


    /** 按 path 判断文档是否已存在（工单同步以 path=ticket/#{id} 作为幂等键）。 */
    boolean existsByPath(String path);

    /** 演示模式（无租户上下文）下列表按产品线过滤。 */
    List<KbDoc> findByProductLine(String productLine);

    /** 按租户过滤知识库文档（列表默认查询）。 */
    List<KbDoc> findByTenantId(Long tenantId);

    /** 按租户 + 产品线过滤知识库文档（列表带产品线筛选）。 */
    List<KbDoc> findByTenantIdAndProductLine(Long tenantId, String productLine);

    /** 分页：演示模式下按产品线过滤。 */
    Page<KbDoc> findByProductLine(String productLine, Pageable pageable);

    /** 分页：按租户过滤。 */
    Page<KbDoc> findByTenantId(Long tenantId, Pageable pageable);

    /** 分页：按租户 + 产品线过滤。 */
    Page<KbDoc> findByTenantIdAndProductLine(Long tenantId, String productLine, Pageable pageable);

    /** 租户下出现过的产品线（用于筛选下拉，不受分页影响）。 */
    @Query("select distinct d.productLine from KbDoc d where d.tenantId = :tenantId "
            + "and d.productLine is not null and d.productLine <> ''")
    List<String> findProductLinesByTenant(@Param("tenantId") Long tenantId);

    /** 全量产品线（演示模式）。 */
    @Query("select distinct d.productLine from KbDoc d where d.productLine is not null and d.productLine <> ''")
    List<String> findAllProductLines();
}

