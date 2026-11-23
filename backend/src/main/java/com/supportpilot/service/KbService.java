package com.supportpilot.service;

import com.supportpilot.common.TenantContext;
import com.supportpilot.dto.KbDocPageVO;
import com.supportpilot.dto.KbDocVO;
import com.supportpilot.entity.KbDoc;
import com.supportpilot.entity.Ticket;
import com.supportpilot.repository.KbDocRepository;
import com.supportpilot.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KbService {

    private final KbDocRepository kbDocRepository;
    private final KbIndexService kbIndexService;
    private final TicketRepository ticketRepository;
    /** 自引用代理：用于在新线程中触发带事务的 ingestTicket（避免自调用绕过代理）。 */
    private final ObjectProvider<KbService> selfProvider;

    public KbDocPageVO list(String productLine, int page, int size) {
        Long tid = TenantContext.get();
        String pl = (productLine == null || productLine.isBlank()) ? null : productLine.trim();
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(p - 1, s, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<KbDoc> result;
        if (tid == null) {
            // 演示模式：无租户上下文，展示全部（可按产品线过滤）
            result = pl == null
                    ? kbDocRepository.findAll(pageable)
                    : kbDocRepository.findByProductLine(pl, pageable);
        } else {
            result = pl == null
                    ? kbDocRepository.findByTenantId(tid, pageable)
                    : kbDocRepository.findByTenantIdAndProductLine(tid, pl, pageable);
        }
        List<String> productLines = (tid == null
                ? kbDocRepository.findAllProductLines()
                : kbDocRepository.findProductLinesByTenant(tid)).stream().sorted().toList();
        return new KbDocPageVO(result.getContent().stream().map(this::toVO).toList(),
                result.getTotalElements(), p, s, productLines);
    }

    @Transactional
    public Map<String, Object> reindex() {
        return kbIndexService.rebuild();
    }

    /** 删除单篇文档（含向量与分片）。非演示模式下校验文档归属当前租户。 */
    @Transactional
    public Map<String, Object> delete(Long docId) {
        if (!ownsDoc(docId)) {
            return Map.of("deleted", false, "message", "文档不存在或无权操作");
        }
        return kbIndexService.deleteDoc(docId);
    }

    /** 单篇重建索引。非演示模式下校验文档归属当前租户。 */
    @Transactional
    public Map<String, Object> reindexDoc(Long docId) {
        if (!ownsDoc(docId)) {
            return Map.of("indexed", 0, "message", "文档不存在或无权操作");
        }
        return kbIndexService.reindexDoc(docId);
    }

    /** 校验文档存在且属于当前租户；演示模式（tid 为 null）不限制。 */
    private boolean ownsDoc(Long docId) {
        Long tid = TenantContext.get();
        KbDoc doc = kbDocRepository.findById(docId).orElse(null);
        if (doc == null) {
            return false;
        }
        return tid == null || tid.equals(doc.getTenantId());
    }

    public List<Map<String, Object>> search(String query) {
        return kbIndexService.search(query);
    }

    @Transactional
    public Map<String, Object> ingest(String name, String content, String productLine) {
        return kbIndexService.ingest(name, content, productLine);
    }

    /**
     * 把单条工单沉淀进知识库（含会话消息）。会用 findDetailById 拉取消息/租户/产品，
     * 确保懒加载字段在事务内可用。幂等：已存在 path=ticket/#{id} 则跳过。
     */
    @Transactional
    public Map<String, Object> ingestTicket(Long ticketId) {
        Ticket t = ticketRepository.findDetailById(ticketId).orElse(null);
        if (t == null) {
            return Map.of("status", "失败", "message", "工单不存在", "ticketId", ticketId);
        }
        return kbIndexService.ingestTicket(t);
    }

    /**
     * 批量同步所有「已解决」工单进知识库。返回总数/入库/跳过/失败统计，幂等可重复执行。
     */
    @Transactional
    public Map<String, Object> syncResolvedTickets() {
        List<Ticket> resolved = ticketRepository.findByStatus("已解决");
        int ingested = 0, skipped = 0, failed = 0;
        List<String> details = new ArrayList<>();
        for (Ticket t : resolved) {
            try {
                Map<String, Object> r = ingestTicket(t.getId());
                if (Boolean.TRUE.equals(r.get("skipped"))) {
                    skipped++;
                } else if ("失败".equals(r.get("status"))) {
                    failed++;
                    details.add("#" + t.getId() + ": " + r.getOrDefault("message", ""));
                } else {
                    ingested++;
                }
            } catch (Exception e) {
                failed++;
                details.add("#" + t.getId() + ": " + e.getMessage());
                log.warn("同步已解决工单失败 id={}", t.getId(), e);
            }
        }
        log.info("syncResolvedTickets 完成：共 {}，入库 {}，跳过已存在 {}，失败 {}",
                resolved.size(), ingested, skipped, failed);
        Map<String, Object> resp = new HashMap<>();
        resp.put("total", resolved.size());
        resp.put("ingested", ingested);
        resp.put("skipped", skipped);
        resp.put("failed", failed);
        if (!details.isEmpty()) {
            resp.put("details", details);
        }
        return resp;
    }

    /**
     * 事务提交后在虚拟线程上异步入库单条工单：不阻塞主流程、失败不影响主流程。
     * 用 selfProvider 走代理调用，保证新线程上 @Transactional 生效。
     */
    public void ingestTicketAsync(Long ticketId) {
        Runnable task = () -> {
            try {
                selfProvider.getObject().ingestTicket(ticketId);
            } catch (Exception e) {
                log.warn("工单异步入库失败 ticketId={}", ticketId, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    Thread.startVirtualThread(task);
                }
            });
        } else {
            Thread.startVirtualThread(task);
        }
    }

    private KbDocVO toVO(KbDoc d) {
        KbDocVO vo = new KbDocVO();
        vo.setId(d.getId());
        vo.setName(d.getName());
        vo.setPath(d.getPath());
        vo.setProductLine(d.getProductLine());
        vo.setChunks(d.getChunks());
        vo.setHits(d.getHits());
        vo.setStatus(d.getStatus());
        return vo;
    }
}
