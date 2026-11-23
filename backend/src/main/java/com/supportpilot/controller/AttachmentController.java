package com.supportpilot.controller;

import com.supportpilot.common.Result;
import com.supportpilot.common.Role;
import com.supportpilot.common.audit.OperationAudit;
import com.supportpilot.common.security.RequireRole;
import com.supportpilot.dto.AttachmentVO;
import com.supportpilot.entity.Attachment;
import com.supportpilot.service.AttachmentService;
import com.supportpilot.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 工单附件（V2.1 M4）：上传 / 列表 / 鉴权下载 / 删除 / 可见性 / 管理员清理。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final TicketService ticketService;

    @OperationAudit(module = "attachment", action = "upload", targetType = "ticket",
            targetId = "#ticketId", summary = "工单 #{ticketId} 上传附件")
    @PostMapping("/tickets/{ticketId}/attachments")
    public Result<AttachmentVO> upload(@PathVariable Long ticketId,
                                       @RequestParam("file") MultipartFile file,
                                       @RequestParam(required = false) Long messageId,
                                       @RequestParam(required = false) String visibility) {
        ticketService.requireVisibleTicket(ticketId);
        return Result.ok(attachmentService.upload(ticketId, file, messageId, visibility));
    }

    @GetMapping("/tickets/{ticketId}/attachments")
    public Result<List<AttachmentVO>> list(@PathVariable Long ticketId) {
        ticketService.requireVisibleTicket(ticketId);
        return Result.ok(attachmentService.listByTicket(ticketId));
    }

    /** 鉴权下载：先校验工单数据范围，再读文件；文件名按 RFC 5987 编码。 */
    @GetMapping("/attachments/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Attachment a = attachmentService.requireAttachment(id);
        ticketService.requireVisibleTicket(a.getTicketId());
        Resource resource = attachmentService.loadResource(a);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(a.getFileName(), StandardCharsets.UTF_8)
                .build();
        MediaType type;
        try {
            type = a.getMime() == null ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(a.getMime());
        } catch (Exception e) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(type)
                .body(resource);
    }

    @OperationAudit(module = "attachment", action = "delete", targetType = "attachment",
            targetId = "#id", summary = "删除附件 #{id}")
    @DeleteMapping("/attachments/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Attachment a = attachmentService.requireAttachment(id);
        ticketService.requireVisibleTicket(a.getTicketId());
        attachmentService.delete(id);
        return Result.ok();
    }

    @OperationAudit(module = "attachment", action = "update", targetType = "attachment",
            targetId = "#id", summary = "修改附件 #{id} 可见性")
    @PutMapping("/attachments/{id}/visibility")
    public Result<AttachmentVO> visibility(@PathVariable Long id,
                                           @RequestParam String visibility) {
        Attachment a = attachmentService.requireAttachment(id);
        ticketService.requireVisibleTicket(a.getTicketId());
        return Result.ok(attachmentService.changeVisibility(id, visibility));
    }

    /** 清理预览（仅管理员）：列出满 180 天的附件，不删除。 */
    @RequireRole({Role.ADMIN})
    @GetMapping("/attachments/cleanup/preview")
    public Result<Map<String, Object>> cleanupPreview() {
        return Result.ok(attachmentService.cleanupPreview());
    }

    /** 执行清理（仅管理员）：物理删除文件并标记 purged=1。 */
    @RequireRole({Role.ADMIN})
    @OperationAudit(module = "attachment", action = "purge", targetType = "attachment", summary = "执行附件清理")
    @PostMapping("/attachments/cleanup/execute")
    public Result<Map<String, Object>> cleanupExecute() {
        return Result.ok(attachmentService.cleanupExecute());
    }

    /** 容量统计：当前总量与配额。 */
    @RequireRole({Role.ADMIN, Role.MANAGER})
    @GetMapping("/attachments/stats")
    public Result<Map<String, Object>> stats() {
        long total = attachmentService.totalSize();
        return Result.ok(Map.of(
                "totalBytes", total,
                "totalText", AttachmentService.sizeText(total)
        ));
    }
}
