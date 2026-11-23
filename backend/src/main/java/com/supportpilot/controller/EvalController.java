package com.supportpilot.controller;

import com.supportpilot.common.Result;
import com.supportpilot.common.Role;
import com.supportpilot.common.audit.OperationAudit;
import com.supportpilot.common.security.RequireRole;
import com.supportpilot.entity.AiEvalCase;
import com.supportpilot.service.EvalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** M3 AI 评测：采纳率、用例沉淀、回归评测与低分聚类。 */
@RestController
@RequestMapping("/api/eval")
@RequiredArgsConstructor
public class EvalController {

    private final EvalService evalService;

    public record SedimentRequest(Long ticketId, Long draftId, String question, String expectedPoints) {
    }

    public record RunRequest(String versionLabel) {
    }

    @RequireRole({Role.ADMIN, Role.MANAGER, Role.ENGINEER})
    @GetMapping("/adoption")
    public Result<Map<String, Object>> adoption() {
        return Result.ok(evalService.adoption());
    }

    @RequireRole({Role.ADMIN, Role.MANAGER})
    @OperationAudit(module = "eval", action = "sediment", targetType = "eval_case", summary = "沉淀评测用例")
    @PostMapping("/cases/sediment")
    public Result<AiEvalCase> sediment(@RequestBody SedimentRequest request) {
        return Result.ok(evalService.sediment(request.ticketId(), request.draftId(),
                request.question(), request.expectedPoints()));
    }

    @RequireRole({Role.ADMIN, Role.MANAGER, Role.ENGINEER})
    @GetMapping("/cases")
    public Result<List<AiEvalCase>> cases() {
        return Result.ok(evalService.cases());
    }

    @RequireRole({Role.ADMIN, Role.MANAGER})
    @OperationAudit(module = "eval", action = "run", targetType = "eval_run", summary = "执行回归评测")
    @PostMapping("/runs")
    public Result<Map<String, Object>> run(@RequestBody(required = false) RunRequest request) {
        return Result.ok(evalService.run(request == null ? null : request.versionLabel()));
    }

    @RequireRole({Role.ADMIN, Role.MANAGER, Role.ENGINEER})
    @GetMapping("/clusters")
    public Result<List<Map<String, Object>>> clusters() {
        return Result.ok(evalService.clusters());
    }
}
