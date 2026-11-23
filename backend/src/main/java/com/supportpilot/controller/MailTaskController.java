package com.supportpilot.controller;

import com.supportpilot.common.Result;
import com.supportpilot.common.Role;
import com.supportpilot.common.audit.OperationAudit;
import com.supportpilot.common.security.RequireRole;
import com.supportpilot.dto.MailTaskVO;
import com.supportpilot.service.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 邮件任务管理（V2.0 EP3），仅管理员：查看失败任务并人工重发。
 */
@RestController
@RequestMapping("/api/mail-tasks")
@RequiredArgsConstructor
@RequireRole(Role.ADMIN)
public class MailTaskController {

    private final MailService mailService;

    @GetMapping
    public Result<List<MailTaskVO>> list(@RequestParam(required = false) String status) {
        return Result.ok(mailService.list(status));
    }

    @OperationAudit(module = "notify", action = "resend", targetType = "mail_task",
            targetId = "#id", summary = "重发邮件任务 #{id}")
    @PostMapping("/{id}/resend")
    public Result<MailTaskVO> resend(@PathVariable Long id) {
        return Result.ok(mailService.resend(id));
    }
}
