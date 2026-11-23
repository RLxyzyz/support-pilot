package com.supportpilot.service;

import com.supportpilot.common.TenantContext;
import com.supportpilot.entity.KbChunk;
import com.supportpilot.entity.KbDoc;
import com.supportpilot.entity.Ticket;
import com.supportpilot.entity.TicketMessage;
import com.supportpilot.repository.KbChunkRepository;
import com.supportpilot.repository.KbDocRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库向量化服务：文档导入（切分 → 写 kb_chunk → 写 Milvus）、重建索引、相似度检索。
 *
 * <p>VectorStore 为可选依赖（仅真实模式 + embedding 就绪时存在）；不可用时方法返回
 * 提示信息而非抛异常，保证 mock 模式下知识库页面不报错。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbIndexService {

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final KbChunkRepository kbChunkRepository;
    private final KbDocRepository kbDocRepository;

    @Value("${app.rag.top-k:5}")
    private int topK;

    @Value("${app.rag.similarity-threshold:0.3}")
    private double similarityThreshold;

    @Value("${app.kb.chunk-size:500}")
    private int chunkSize;

    @Value("${app.kb.chunk-overlap:80}")
    private int chunkOverlap;

    @Value("${spring.ai.openai.embedding.base-url:}")
    private String embBaseUrl;

    @Value("${spring.ai.openai.embedding.api-key:}")
    private String embApiKey;

    @Value("${spring.ai.openai.embedding.options.model:}")
    private String embModel;

    @PostConstruct
    void diagEmbeddingConfig() {
        String masked = (embApiKey == null || embApiKey.isBlank()) ? "(empty)"
                : embApiKey.substring(0, Math.min(6, embApiKey.length())) + "***(len=" + embApiKey.length() + ")";
        log.info("[Embedding诊断] base-url=[{}], model=[{}], apiKey={}", embBaseUrl, embModel, masked);
    }

    /**
     * 重建索引：清空旧向量后，把全量 kb_chunk 重新 embedding 写入 Milvus。
     */
    @Transactional
    public Map<String, Object> rebuild() {
        VectorStore vs = vectorStoreProvider.getIfAvailable();
        if (vs == null) {
            return Map.of("indexed", 0, "message",
                    "向量库未就绪：请配置 AI_EMBEDDING_API_KEY 并将 app.ai.mock-enabled 设为 false");
        }
        List<KbChunk> chunks = kbChunkRepository.findAll();
        if (chunks.isEmpty()) {
            return Map.of("indexed", 0, "message", "暂无知识分片，请先上传文档内容");
        }
        try {
            List<String> ids = chunks.stream().map(c -> String.valueOf(c.getId())).toList();
            // 幂等：先删旧向量（Milvus 删除不存在的主键不会报错），再全量写入
            vs.delete(ids);
            List<Document> docs = new ArrayList<>(chunks.size());
            Map<Long, KbDoc> docCache = new HashMap<>();
            for (KbChunk c : chunks) {
                KbDoc doc = resolveDoc(c.getDocId(), docCache);
                docs.add(toDocument(c, doc));
            }
            vs.add(docs);
            markAllIndexed();
            log.info("KbIndexService.rebuild 完成，写入 {} 条向量", docs.size());
            return Map.of("indexed", docs.size(), "message", "重建索引完成");
        } catch (Exception e) {
            log.error("KbIndexService.rebuild 失败", e);
            return Map.of("indexed", 0, "message", "重建索引失败：" + e.getMessage());
        }
    }

    /**
     * 单篇重新索引：删该文档旧向量 → 用现有 kb_chunk 重新向量化写入 → 更新状态。
     * 用于修正单篇向量 metadata（如补齐 tenant_id）而不触发全量重建。
     */
    @Transactional
    public Map<String, Object> reindexDoc(Long docId) {
        KbDoc doc = kbDocRepository.findById(docId).orElse(null);
        if (doc == null) {
            return Map.of("indexed", 0, "message", "文档不存在");
        }
        VectorStore vs = vectorStoreProvider.getIfAvailable();
        if (vs == null) {
            return Map.of("indexed", 0, "message",
                    "向量库未就绪：请配置 AI_EMBEDDING_API_KEY 并将 app.ai.mock-enabled 设为 false");
        }
        List<KbChunk> chunks = kbChunkRepository.findByDocId(docId);
        if (chunks.isEmpty()) {
            return Map.of("indexed", 0, "message", "该文档无分片，无法重建");
        }
        try {
            List<String> ids = chunks.stream().map(c -> String.valueOf(c.getId())).toList();
            vs.delete(ids); // 幂等：先删旧向量
            List<Document> docs = chunks.stream().map(c -> toDocument(c, doc)).toList();
            vs.add(docs);
            doc.setStatus("已索引");
            kbDocRepository.save(doc);
            log.info("KbIndexService.reindexDoc 文档 {} 重建完成，写入 {} 条向量", docId, docs.size());
            return Map.of("indexed", docs.size(), "message", "单篇重建索引完成");
        } catch (Exception e) {
            doc.setStatus("索引失败");
            kbDocRepository.save(doc);
            log.error("KbIndexService.reindexDoc 失败 docId={}", docId, e);
            return Map.of("indexed", 0, "message", "重建索引失败：" + e.getMessage());
        }
    }

    /**
     * 删除单篇文档：清 Milvus 向量（按 chunk_id）+ 删 kb_chunk + 删 kb_doc。
     * 向量删除失败不阻塞库表清理（避免残留脏数据）。docId 不存在视为幂等。
     */
    @Transactional
    public Map<String, Object> deleteDoc(Long docId) {
        KbDoc doc = kbDocRepository.findById(docId).orElse(null);
        if (doc == null) {
            return Map.of("deleted", false, "message", "文档不存在");
        }
        List<KbChunk> chunks = kbChunkRepository.findByDocId(docId);
        VectorStore vs = vectorStoreProvider.getIfAvailable();
        if (vs != null && !chunks.isEmpty()) {
            try {
                List<String> ids = chunks.stream().map(c -> String.valueOf(c.getId())).toList();
                vs.delete(ids);
            } catch (Exception e) {
                log.warn("删除文档向量失败 docId={}，继续清理库表: {}", docId, e.getMessage());
            }
        }
        kbChunkRepository.deleteByDocId(docId);
        kbDocRepository.delete(doc);
        log.info("KbIndexService.deleteDoc 文档 {} 已删除，清理 {} 个分片", docId, chunks.size());
        return Map.of("deleted", true, "chunks", chunks.size());
    }

    /**
     * 相似度检索：返回命中片段列表（供前端知识库搜索 / Agent 引用展示）。
     */
    public List<Map<String, Object>> search(String query) {
        VectorStore vs = vectorStoreProvider.getIfAvailable();
        if (vs == null || query == null || query.isBlank()) {
            return List.of();
        }
        try {
            Long tid = TenantContext.get();
            SearchRequest.Builder reqBuilder = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(similarityThreshold);
            // 下推 Milvus 过滤：仅检索当前租户的向量
            if (tid != null) {
                reqBuilder.filterExpression("tenant_id == " + tid);
            }
            List<Document> hits = vs.similaritySearch(reqBuilder.build());
            // Java 层兜底过滤：防止 filterExpression 未生效时跨租户泄露
            if (tid != null) {
                hits = hits.stream()
                        .filter(d -> tid.equals(toTenantId(d.getMetadata() == null ? null : d.getMetadata().get("tenant_id"))))
                        .toList();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (Document d : hits) {
                Map<String, Object> md = d.getMetadata();
                Map<String, Object> item = new HashMap<>();
                item.put("id", md == null ? "" : String.valueOf(md.getOrDefault("chunk_id", "")));
                item.put("doc", md == null ? "" : String.valueOf(md.getOrDefault("doc_name", "")));
                item.put("score", d.getScore() == null ? 0.0 : d.getScore());
                item.put("content", d.getText() == null ? "" : d.getText());
                result.add(item);
            }
            return result;
        } catch (Exception e) {
            log.warn("KbIndexService.search 异常: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 导入一篇手动上传文档：按段落/长度切分 → 写 kb_chunk → 向量化写入 Milvus → 更新 kb_doc 状态。
     */
    @Transactional
    public Map<String, Object> ingest(String name, String content, String productLine) {
        String path = (productLine == null || productLine.isBlank()) ? "manual" : "manual/" + productLine;
        return persistDocument(name, content, productLine, path, TenantContext.get());
    }

    /**
     * 将一条已解决工单沉淀为知识库文档：构建工单全文（标题/元信息/问题描述/对外会话与解决方案），
     * 复用 {@link #persistDocument} 切分并向量化。以 path=ticket/#{id} 幂等，已存在则跳过。
     * 内部备注（internal=true）不会写入知识库，避免泄露团队内部信息。
     */
    @Transactional
    public Map<String, Object> ingestTicket(Ticket ticket) {
        if (ticket == null || ticket.getId() == null) {
            return Map.of("status", "失败", "message", "工单为空");
        }
        String path = "ticket/#" + ticket.getId();
        if (kbDocRepository.existsByPath(path)) {
            return Map.of("status", "已存在", "skipped", true, "ticketId", ticket.getId(), "path", path);
        }
        String content = buildTicketContent(ticket);
        if (content.isBlank()) {
            return Map.of("status", "失败", "message", "工单内容为空", "ticketId", ticket.getId());
        }
        String name = "[已解决工单] " + nz(ticket.getTitle());
        if (name.length() > 60) {
            name = name.substring(0, 60) + "…";
        }
        String productLine = ticket.getProduct() != null && ticket.getProduct().getName() != null
                ? ticket.getProduct().getName() : ticket.getCategory();
        Long tenantId = ticket.getTenant() != null ? ticket.getTenant().getId() : null;
        Map<String, Object> resp = new HashMap<>(persistDocument(name, content, productLine, path, tenantId));
        resp.put("ticketId", ticket.getId());
        resp.put("path", path);
        log.info("KbIndexService.ingestTicket 工单 #{} 入库完成，path={}，状态={}", ticket.getId(), path, resp.get("status"));
        return resp;
    }

    /**
     * 通用文档持久化：切分 → 写 kb_doc/kb_chunk → 向量化写 Milvus → 更新状态。
     * 手动上传与工单沉淀共用此链路，保证文本与向量一致。
     */
    private Map<String, Object> persistDocument(String name, String content, String productLine,
                                                String path, Long tenantId) {
        if (content == null || content.isBlank()) {
            return Map.of("chunks", 0, "status", "失败", "message", "文档内容为空");
        }
        String docName = (name == null || name.isBlank()) ? "未命名文档" : name.trim();
        List<String> parts = split(content);

        KbDoc doc = new KbDoc();
        doc.setName(docName);
        doc.setProductLine(productLine);
        doc.setPath(path);
        doc.setChunks(parts.size());
        doc.setHits(0);
        doc.setStatus("索引中");
        doc.setTenantId(tenantId);
        doc.setCreatedAt(Instant.now());
        doc = kbDocRepository.save(doc);

        List<Document> docs = new ArrayList<>(parts.size());
        for (String part : parts) {
            KbChunk chunk = new KbChunk();
            chunk.setDocId(doc.getId());
            chunk.setContent(part);
            chunk.setCreatedAt(Instant.now());
            chunk = kbChunkRepository.save(chunk);
            docs.add(toDocument(chunk, doc));
        }

        VectorStore vs = vectorStoreProvider.getIfAvailable();
        String errorMsg = null;
        if (vs != null && !docs.isEmpty()) {
            try {
                // DashScope Embedding 单次请求最多 10 条，按 10 条一批写入
                final int EMBEDDING_BATCH_SIZE = 10;
                for (int i = 0; i < docs.size(); i += EMBEDDING_BATCH_SIZE) {
                    int end = Math.min(i + EMBEDDING_BATCH_SIZE, docs.size());
                    List<Document> batch = docs.subList(i, end);
                    vs.add(batch);
                }
                doc.setStatus("已索引");
            } catch (Exception e) {
                String detail = describeThrowable(e);
                errorMsg = e.getClass().getSimpleName() + ": " + e.getMessage();
                log.warn("文档写入向量库失败，docId={}，异常链详情：\n{}", doc.getId(), detail, e);
                doc.setStatus("索引失败");
            }
        } else {
            doc.setStatus("待索引");
            errorMsg = "VectorStore 未就绪：请检查 AI_EMBEDDING_API_KEY 是否已配置、Milvus 是否可达";
        }
        kbDocRepository.save(doc);
        log.info("KbIndexService.persistDocument 文档 {} 切分 {} 片，状态 {}", doc.getId(), parts.size(), doc.getStatus());
        Map<String, Object> resp = new HashMap<>();
        resp.put("docId", doc.getId());
        resp.put("chunks", parts.size());
        resp.put("status", doc.getStatus());
        if (errorMsg != null) {
            resp.put("message", errorMsg);
        }
        return resp;
    }

    /** 把一条工单拼成可供检索的自然语言全文（含对外会话，不含内部备注）。 */
    private String buildTicketContent(Ticket t) {
        StringBuilder sb = new StringBuilder();
        sb.append("【历史已解决工单 #").append(t.getId()).append("】").append(nz(t.getTitle())).append("\n");
        sb.append("分类：").append(nz(t.getCategory()))
                .append("｜优先级：").append(nz(t.getPriority()))
                .append("｜产品：").append(t.getProduct() != null ? nz(t.getProduct().getName()) : "")
                .append("｜客户：").append(t.getTenant() != null ? nz(t.getTenant().getName()) : "");
        sb.append("\n\n问题描述：\n").append(nz(t.getBody()));
        if (t.getMessages() != null && !t.getMessages().isEmpty()) {
            sb.append("\n\n会话记录与解决方案：\n");
            for (TicketMessage m : t.getMessages()) {
                if (Boolean.TRUE.equals(m.getInternal())) {
                    continue; // 内部备注不进知识库，避免泄露
                }
                sb.append("- [").append(nz(m.getRole())).append(" ").append(nz(m.getWho())).append("] ")
                        .append(nz(m.getText())).append("\n");
            }
        }
        return sb.toString();
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    /** 深挖异常链，提取每层异常的 message；若是 HTTP 状态异常，额外打印状态码与响应体，便于定位 404/401 根因。 */
    private String describeThrowable(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = e;
        int depth = 0;
        while (cur != null && depth < 15) {
            sb.append("  [").append(depth).append("] ")
                    .append(cur.getClass().getName()).append(": ").append(cur.getMessage()).append("\n");
            if (cur instanceof HttpStatusCodeException hse) {
                sb.append("      -> HTTP ").append(hse.getStatusCode().value())
                        .append(" | responseBody: ").append(hse.getResponseBodyAsString()).append("\n");
            }
            if (cur.getCause() == cur) {
                break;
            }
            cur = cur.getCause();
            depth++;
        }
        return sb.toString();
    }

    private KbDoc resolveDoc(Long docId, Map<Long, KbDoc> cache) {
        if (docId == null) {
            return null;
        }
        return cache.computeIfAbsent(docId, id -> kbDocRepository.findById(id).orElse(null));
    }

    private Document toDocument(KbChunk chunk, KbDoc doc) {
        Map<String, Object> md = new HashMap<>();
        md.put("chunk_id", chunk.getId());
        if (doc != null) {
            md.put("doc_id", doc.getId());
            md.put("doc_name", doc.getName());
            md.put("doc_path", doc.getPath());
            if (doc.getProductLine() != null) {
                md.put("product_line", doc.getProductLine());
            }
            // 租户隔离：写入 tenant_id 供检索时 filterExpression 下推 + Java 层兜底过滤
            if (doc.getTenantId() != null) {
                md.put("tenant_id", doc.getTenantId());
            }
        }
        return new Document(String.valueOf(chunk.getId()), chunk.getContent(), md);
    }

    /** metadata 中取出的 tenant_id 可能是 Long/Integer/String，统一转 Long 比较；取不到返回 null。 */
    private static Long toTenantId(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void markAllIndexed() {
        for (KbDoc doc : kbDocRepository.findAll()) {
            if (!"已索引".equals(doc.getStatus())) {
                doc.setStatus("已索引");
                kbDocRepository.save(doc);
            }
        }
    }

    /**
     * 轻量切分：先按空行分段，段落过长再按 chunkSize 字符滑窗切（带 overlap）。
     */
    private List<String> split(String text) {
        List<String> result = new ArrayList<>();
        String[] paras = text.split("\\r?\\n\\r?\\n");
        for (String p : paras) {
            String trimmed = p.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() <= chunkSize) {
                result.add(trimmed);
            } else {
                int step = Math.max(1, chunkSize - chunkOverlap);
                for (int i = 0; i < trimmed.length(); i += step) {
                    result.add(trimmed.substring(i, Math.min(trimmed.length(), i + chunkSize)));
                }
            }
        }
        return result;
    }
}
