package com.supportpilot.rag;

import com.supportpilot.entity.KbDoc;
import com.supportpilot.entity.Ticket;
import com.supportpilot.repository.KbDocRepository;
import com.supportpilot.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 真实 RAG：app.ai.mock-enabled=false 时启用。
 *
 * <p>优先用 Milvus 向量库做相似度检索（VectorStore），把命中的知识片段正文拼入 prompt；
 * 当向量库不可用 / 无数据 / 检索异常时，回退到基于 kb_doc 元数据的关键词轻量匹配作为保底，
 * 避免 prompt 上下文为空导致模型以“缺少上下文”为由拒答。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ai.mock-enabled", havingValue = "false")
public class RealRagService implements RagService {

    private final TicketRepository ticketRepository;
    private final KbDocRepository kbDocRepository;
    private final ObjectProvider<VectorStore> vectorStoreProvider;

    @Value("${app.rag.top-k:5}")
    private int topK;

    @Value("${app.rag.similarity-threshold:0.3}")
    private double similarityThreshold;

    @Override
    public RagResult retrieve(String ticketId) {
        Ticket ticket = loadTicket(ticketId);
        Long tenantId = ticket != null && ticket.getTenant() != null ? ticket.getTenant().getId() : null;
        String query = buildQuery(ticket);
        RagResult vector = retrieveByVector(query, tenantId);
        if (vector.hit()) {
            return vector;
        }
        return retrieveByKeyword(ticket, tenantId);
    }

    private static Long toLong(Object o) {
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

    private Ticket loadTicket(String ticketId) {
        Long id = parseId(ticketId);
        return id == null ? null : ticketRepository.findById(id).orElse(null);
    }

    private String buildQuery(Ticket t) {
        if (t == null) {
            return "";
        }
        StringBuilder q = new StringBuilder();
        if (t.getTitle() != null) {
            q.append(t.getTitle());
        }
        if (t.getBody() != null && !t.getBody().isBlank()) {
            if (!q.isEmpty()) {
                q.append(" ");
            }
            q.append(t.getBody());
        }
        return q.toString().trim();
    }

    /** metadata 中的 tenant_id 可能是 Long/Integer/String，统一转 Long；取不到返回 null。 */
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

    /**
     * 向量相似度检索：用工单标题 + 正文作为 query，从 Milvus 取 topK 片段。
     * 仅检索工单所属租户的向量（filterExpression 下推 + Java 层兜底过滤）。
     * 无结果 / 异常时返回 {@link RagResult#EMPTY}，由调用方回退关键词匹配。
     */
    private RagResult retrieveByVector(String query, Long tenantId) {
        VectorStore vs = vectorStoreProvider.getIfAvailable();
        if (vs == null) {
            log.debug("RealRagService 未注入 VectorStore（embedding/向量库未就绪），跳过向量检索");
            return RagResult.EMPTY;
        }
        if (query == null || query.isBlank()) {
            return RagResult.EMPTY;
        }
        try {
            SearchRequest.Builder reqBuilder = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(similarityThreshold);
            // 下推 Milvus 过滤：仅检索工单所属租户的向量
            if (tenantId != null) {
                reqBuilder.filterExpression("tenant_id == " + tenantId);
            }
            List<Document> hits = vs.similaritySearch(reqBuilder.build());
            // Java 层兜底过滤：防止 filterExpression 未生效时跨租户泄露
            if (tenantId != null) {
                hits = hits.stream()
                        .filter(d -> tenantId.equals(toTenantId(d.getMetadata() == null ? null : d.getMetadata().get("tenant_id"))))
                        .toList();
            }
            if (hits == null || hits.isEmpty()) {
                log.info("RealRagService 向量检索无命中，tenantId={}", tenantId);
                return RagResult.EMPTY;
            }
            List<String> lines = new ArrayList<>();
            List<Long> docIds = new ArrayList<>();
            int idx = 1;
            for (Document d : hits) {
                Map<String, Object> md = d.getMetadata();
                Long did = toLong(md == null ? null : md.get("doc_id"));
                if (did != null && !docIds.contains(did)) {
                    docIds.add(did);
                }
                String name = md == null ? null : (String) md.get("doc_name");
                String path = md == null ? null : (String) md.get("doc_path");
                StringBuilder sb = new StringBuilder("[").append(idx++).append("] ");
                sb.append(name == null ? "知识片段" : name);
                if (path != null) {
                    sb.append(" —— ").append(path);
                }
                if (d.getScore() != null) {
                    sb.append(" · 相似度 ").append(String.format("%.2f", d.getScore()));
                }
                String text = d.getText();
                if (text != null && !text.isBlank()) {
                    String snippet = text.length() > 300 ? text.substring(0, 300) + "…" : text;
                    sb.append("\n    ").append(snippet);
                }
                lines.add(sb.toString());
            }
            log.info("RealRagService 向量检索命中 {} 条，tenantId={}", hits.size(), tenantId);
            return new RagResult(String.join("\n", lines), docIds, !docIds.isEmpty());
        } catch (Exception e) {
            log.warn("RealRagService 向量检索异常，回退关键词匹配，tenantId={}: {}", tenantId, e.getMessage());
            return RagResult.EMPTY;
        }
    }

    // ==================== 关键词保底（向量库不可用时使用） ====================

    private RagResult retrieveByKeyword(Ticket ticket, Long tenantId) {
        List<String> hits = new ArrayList<>();
        List<Long> docIds = new ArrayList<>();
        try {
            String titleLower = ticket == null || ticket.getTitle() == null
                    ? "" : ticket.getTitle().toLowerCase(Locale.ROOT);

            int idx = 1;
            for (KbDoc doc : kbDocRepository.findAll()) {
                // 租户隔离：仅匹配当前租户的文档（tenantId 为 null 表示演示模式，不过滤）
                if (tenantId != null && !tenantId.equals(doc.getTenantId())) {
                    continue;
                }
                if (match(titleLower, doc)) {
                    if (doc.getId() != null && !docIds.contains(doc.getId())) {
                        docIds.add(doc.getId());
                    }
                    hits.add("[" + idx + "] " + doc.getName()
                            + (doc.getPath() == null ? "" : " —— " + doc.getPath()));
                    if (++idx > 5) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("RealRagService 关键词检索异常，tenantId={}: {}", tenantId, e.getMessage());
        }

        if (hits.isEmpty()) {
            log.info("RealRagService 未命中知识库文档，tenantId={}", tenantId);
            return RagResult.EMPTY;
        }
        log.info("RealRagService 关键词命中 {} 条，tenantId={}", hits.size(), tenantId);
        return new RagResult(String.join("\n", hits), docIds, !docIds.isEmpty());
    }

    /**
     * 取工单标题中长度>=3 的英文/数字 token，任一命中文档文本即视为相关。
     */
    private boolean match(String titleLower, KbDoc doc) {
        if (titleLower.isEmpty()) {
            return false;
        }
        String docText = ((doc.getName() == null ? "" : doc.getName())
                + " " + (doc.getPath() == null ? "" : doc.getPath())
                + " " + (doc.getProductLine() == null ? "" : doc.getProductLine()))
                .toLowerCase(Locale.ROOT);
        for (String token : titleLower.split("[^a-z0-9]+")) {
            if (token.length() >= 3 && docText.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private Long parseId(String rawId) {
        if (rawId == null) {
            return null;
        }
        try {
            return Long.valueOf(rawId.replace("#", "").trim());
        } catch (Exception e) {
            return null;
        }
    }
}
