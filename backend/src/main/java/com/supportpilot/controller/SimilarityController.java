package com.supportpilot.controller;

import com.supportpilot.common.Result;
import com.supportpilot.common.Role;
import com.supportpilot.common.audit.OperationAudit;
import com.supportpilot.common.security.RequireRole;
import com.supportpilot.entity.SimilarityConfig;
import com.supportpilot.service.SimilarityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** M1 相似工单：相似列表、批量回复、合并与阈值配置。 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SimilarityController {

    private final SimilarityService similarityService;

    public record BatchReplyRequest(String content, List<Long> ticketIds) {
    }

    public record MergeRequest(Long targetId) {
    }

    public record ConfigRequest(Double threshold, Boolean enabled, Integer windowDays) {
    }

    @RequireRole({Role.ADMIN, Role.MANAGER, Role.ENGINEER})
    @GetMapping("/tickets/{id}/similar")
    public Result<Map<String, Object>> similar(@PathVariable Long id) {
        return Result.ok(similarityService.listSimilar(id));
    }

    @RequireRole({Role.ADMIN, Role.MANAGER, Role.ENGINEER})
    @OperationAudit(module = "ticket", action = "batch-reply", targetType = "ticket", summary = "相似工单批量回复")
    @PostMapping("/tickets/{id}/similar/batch-reply")
    public Result<Map<String, Object>> batchReply(@PathVariable Long id,
                                                  @RequestBody BatchReplyRequest request) {
        return Result.ok(similarityService.batchReply(id, request.content(), request.ticketIds()));
    }

    @RequireRole({Role.ADMIN, Role.MANAGER})
    @OperationAudit(module = "ticket", action = "merge", targetType = "ticket", summary = "合并相似工单")
    @PostMapping("/tickets/{id}/similar/merge")
    public Result<Map<String, Object>> merge(@PathVariable Long id,
                                             @RequestBody MergeRequest request) {
        return Result.ok(similarityService.merge(id, request.targetId()));
    }

    @RequireRole({Role.ADMIN, Role.MANAGER, Role.ENGINEER})
    @GetMapping("/similarity/config")
    public Result<SimilarityConfig> config() {
        return Result.ok(similarityService.getConfig());
    }

    @RequireRole({Role.ADMIN})
    @OperationAudit(module = "similarity", action = "update", targetType = "config", summary = "更新相似检测配置")
    @PutMapping("/similarity/config")
    public Result<SimilarityConfig> updateConfig(@RequestBody ConfigRequest request) {
        return Result.ok(similarityService.updateConfig(
                request.threshold(), request.enabled(), request.windowDays()));
    }
}
