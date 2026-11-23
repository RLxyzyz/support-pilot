package com.supportpilot.controller;

import com.supportpilot.common.Result;
import com.supportpilot.common.audit.OperationAudit;
import com.supportpilot.dto.ReplyTemplateRequest;
import com.supportpilot.dto.ReplyTemplateVO;
import com.supportpilot.service.ReplyTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 快捷回复模板（#5）：按租户隔离的 CRUD。 */
@RestController
@RequestMapping("/api/reply-templates")
@RequiredArgsConstructor
public class ReplyTemplateController {

    private final ReplyTemplateService replyTemplateService;

    @GetMapping
    public Result<List<ReplyTemplateVO>> list() {
        return Result.ok(replyTemplateService.list());
    }

    @OperationAudit(module = "reply_template", action = "create", summary = "新增回复模板")
    @PostMapping
    public Result<ReplyTemplateVO> create(@RequestBody ReplyTemplateRequest request) {
        return Result.ok(replyTemplateService.create(request.name(), request.content(), request.category()));
    }

    @OperationAudit(module = "reply_template", action = "update", targetType = "reply_template", targetId = "#id", summary = "修改回复模板 #{id}")
    @PutMapping("/{id}")
    public Result<ReplyTemplateVO> update(@PathVariable Long id, @RequestBody ReplyTemplateRequest request) {
        return Result.ok(replyTemplateService.update(id, request.name(), request.content(), request.category()));
    }

    @OperationAudit(module = "reply_template", action = "delete", targetType = "reply_template", targetId = "#id", summary = "删除回复模板 #{id}")
    @DeleteMapping("/{id}")
    public Result<Object> delete(@PathVariable Long id) {
        replyTemplateService.delete(id);
        return Result.ok();
    }
}
