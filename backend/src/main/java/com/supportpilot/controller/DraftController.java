package com.supportpilot.controller;

import com.supportpilot.common.Result;
import com.supportpilot.common.audit.OperationAudit;
import com.supportpilot.dto.DraftFeedbackStatsVO;
import com.supportpilot.service.DraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/drafts")
@RequiredArgsConstructor
public class DraftController {

    private final DraftService draftService;

    @OperationAudit(module = "draft", action = "approve", targetType = "draft", targetId = "#id", summary = "采纳草稿 #{id}")
    @PostMapping("/{id}/approve")
    public Result<Map<String, Object>> approve(@PathVariable String id,
                                               @RequestBody(required = false) Map<String, String> body) {
        String content = body == null ? null : body.get("content");
        return Result.ok(draftService.approve(id, content));
    }

    @OperationAudit(module = "draft", action = "reject", targetType = "draft", targetId = "#id", summary = "驳回草稿 #{id}")
    @PostMapping("/{id}/reject")
    public Result<Map<String, Object>> reject(@PathVariable String id,
                                              @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        String comment = body == null ? null : body.get("comment");
        return Result.ok(draftService.reject(id, reason, comment));
    }

    /** 草稿反馈统计（采纳/驳回/被编辑数量 + 驳回原因分布），用于看板展示。 */
    @GetMapping("/feedback-stats")
    public Result<DraftFeedbackStatsVO> feedbackStats() {
        return Result.ok(draftService.feedbackStats());
    }
}
