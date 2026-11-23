package com.supportpilot.controller;

import com.supportpilot.common.Result;
import com.supportpilot.common.Role;
import com.supportpilot.common.audit.OperationAudit;
import com.supportpilot.common.security.RequireRole;
import com.supportpilot.dto.KbDocPageVO;
import com.supportpilot.service.KbService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
public class KbController {

    private final KbService kbService;

    @GetMapping("/documents")
    public Result<KbDocPageVO> documents(@RequestParam(required = false) String productLine,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return Result.ok(kbService.list(productLine, page, size));
    }

    /** 删除单篇文档：清 kb_chunk / Milvus 向量 / kb_doc。 */
    @RequireRole({Role.ADMIN, Role.KB_ADMIN})
    @OperationAudit(module = "kb", action = "delete", targetType = "kb_doc", targetId = "#id", summary = "删除知识库文档 #{id}")
    @DeleteMapping("/documents/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        return Result.ok(kbService.delete(id));
    }

    /** 单篇重新索引（先删旧向量再写入），用于修正单篇向量 metadata。 */
    @RequireRole({Role.ADMIN, Role.KB_ADMIN})
    @OperationAudit(module = "kb", action = "reindex", targetType = "kb_doc", targetId = "#id", summary = "重建文档 #{id} 索引")
    @PostMapping("/documents/{id}/reindex")
    public Result<Map<String, Object>> reindexDoc(@PathVariable Long id) {
        return Result.ok(kbService.reindexDoc(id));
    }

    @RequireRole({Role.ADMIN, Role.KB_ADMIN})
    @OperationAudit(module = "kb", action = "create", summary = "上传知识库文档")
    @PostMapping("/documents")
    public Result<Map<String, Object>> upload(@RequestBody(required = false) Map<String, Object> body) {
        String name = body == null ? null : (String) body.get("name");
        String content = body == null ? null : (String) body.get("content");
        String productLine = body == null ? null : (String) body.get("productLine");
        return Result.ok(kbService.ingest(name, content, productLine));
    }

    @RequireRole({Role.ADMIN, Role.KB_ADMIN})
    @OperationAudit(module = "kb", action = "reindex", summary = "重建全部知识库索引")
    @PostMapping("/reindex")
    public Result<Map<String, Object>> reindex() {
        return Result.ok(kbService.reindex());
    }

    /** 批量把所有「已解决」工单同步进知识库（幂等，已入库的会跳过）。 */
    @RequireRole({Role.ADMIN, Role.KB_ADMIN})
    @OperationAudit(module = "kb", action = "sync", summary = "同步已解决工单到知识库")
    @PostMapping("/sync-resolved-tickets")
    public Result<Map<String, Object>> syncResolvedTickets() {
        return Result.ok(kbService.syncResolvedTickets());
    }

    @PostMapping("/search")
    public Result<List<Map<String, Object>>> search(@RequestBody(required = false) Map<String, String> body) {
        String query = body == null ? "" : body.getOrDefault("query", "");
        return Result.ok(kbService.search(query));
    }
}
