package com.supportpilot.controller;

import com.supportpilot.common.Result;
import com.supportpilot.common.Role;
import com.supportpilot.common.audit.OperationAudit;
import com.supportpilot.common.security.RequireRole;
import com.supportpilot.dto.AddTicketMessageRequest;
import com.supportpilot.dto.AssignTicketRequest;
import com.supportpilot.dto.BackfillCustomerRequest;
import com.supportpilot.dto.CreateTicketRequest;
import com.supportpilot.dto.CsatRequest;
import com.supportpilot.dto.RelatedTicketVO;
import com.supportpilot.dto.TicketPageVO;
import com.supportpilot.dto.TicketQuery;
import com.supportpilot.dto.TicketRelationRequest;
import com.supportpilot.dto.TicketVO;
import com.supportpilot.dto.UpdateTicketCategoryRequest;
import com.supportpilot.dto.UpdateTicketPriorityRequest;
import com.supportpilot.dto.UpdateTicketStatusRequest;
import com.supportpilot.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    /** 快速统计：侧边栏角标、工单列表 sub 文案用。 */
    @GetMapping("/quick-stats")
    public Result<Map<String, Object>> quickStats() {
        return Result.ok(ticketService.quickStats());
    }

    @GetMapping
    public Result<TicketPageVO> list(@RequestParam(required = false) String status,
                                     @RequestParam(required = false) String priority,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) Long assigneeId,
                                     @RequestParam(required = false) Long productId,
                                     @RequestParam(required = false) String start,
                                     @RequestParam(required = false) String end,
                                     @RequestParam(required = false) Boolean overdue,
                                     @RequestParam(required = false) Boolean unassigned,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        TicketQuery q = new TicketQuery(status, priority, keyword, assigneeId, productId,
                parseInstant(start), parseInstant(end), overdue, unassigned);
        return Result.ok(ticketService.page(q, page, size));
    }

    /** 导出符合当前筛选条件的全部工单为 CSV（后端生成，数据完整）。 */
    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> export(@RequestParam(required = false) String status,
                                         @RequestParam(required = false) String priority,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Long assigneeId,
                                         @RequestParam(required = false) Long productId,
                                         @RequestParam(required = false) String start,
                                         @RequestParam(required = false) String end,
                                         @RequestParam(required = false) Boolean overdue) {
        TicketQuery q = new TicketQuery(status, priority, keyword, assigneeId, productId,
                parseInstant(start), parseInstant(end), overdue, null);
        String csv = ticketService.exportCsv(q);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=tickets-" + System.currentTimeMillis() + ".csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv);
    }

    private Instant parseInstant(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping("/{id}")
    public Result<TicketVO> get(@PathVariable String id) {
        return Result.ok(ticketService.get(id));
    }

    @OperationAudit(module = "ticket", action = "create", summary = "创建工单")
    @PostMapping
    public Result<TicketVO> create(@Valid @RequestBody CreateTicketRequest request) {
        return Result.ok(ticketService.create(request));
    }

    /** 批量导入工单：接收工单数组，逐条创建，非法条目跳过。 */
    @OperationAudit(module = "ticket", action = "import", summary = "批量导入工单")
    @PostMapping("/import")
    public Result<Map<String, Object>> importAll(@RequestBody List<CreateTicketRequest> requests) {
        return Result.ok(ticketService.importAll(requests));
    }

    /** 存量工单批量归户（PRD EX-03 / RK-01）：仅对未归户工单生效。 */
    @RequireRole({Role.ADMIN, Role.MANAGER})
    @OperationAudit(module = "ticket", action = "update", targetType = "ticket", summary = "存量工单批量归户")
    @PostMapping("/backfill-customer")
    public Result<Map<String, Object>> backfillCustomer(@RequestBody BackfillCustomerRequest request) {
        return Result.ok(ticketService.backfillCustomer(request));
    }

    /** 接单：当前用户成为处理人，状态置为处理中（兼容旧入口）。 */
    @OperationAudit(module = "ticket", action = "accept", targetType = "ticket", targetId = "#id", summary = "接单工单 #{id}")
    @PostMapping("/{id}/accept")
    public Result<TicketVO> accept(@PathVariable String id) {
        return Result.ok(ticketService.accept(id));
    }

    /** 并发认领（V2.1 M3）：已被他人认领返回 409 与当前处理人提示。 */
    @OperationAudit(module = "ticket", action = "claim", targetType = "ticket", targetId = "#id", summary = "认领工单 #{id}")
    @PostMapping("/{id}/claim")
    public Result<TicketVO> claim(@PathVariable String id) {
        return Result.ok(ticketService.claim(id));
    }

    /** 转派：指定目标处理人用户 ID。 */
    @OperationAudit(module = "ticket", action = "assign", targetType = "ticket", targetId = "#id", summary = "转派工单 #{id}")
    @PostMapping("/{id}/assign")
    public Result<TicketVO> assign(@PathVariable String id, @RequestBody AssignTicketRequest request) {
        return Result.ok(ticketService.assign(id, request.assigneeId()));
    }

    /** 升级 L2：状态置为已升级。 */
    @OperationAudit(module = "ticket", action = "escalate", targetType = "ticket", targetId = "#id", summary = "升级工单 #{id}")
    @PostMapping("/{id}/escalate")
    public Result<TicketVO> escalate(@PathVariable String id) {
        return Result.ok(ticketService.escalate(id));
    }

    /** 修改工单状态。 */
    @OperationAudit(module = "ticket", action = "update", targetType = "ticket", targetId = "#id", summary = "修改工单 #{id} 状态")
    @PutMapping("/{id}/status")
    public Result<TicketVO> updateStatus(@PathVariable String id, @RequestBody UpdateTicketStatusRequest request) {
        return Result.ok(ticketService.updateStatus(id, request.status()));
    }

    /** 修改工单优先级。 */
    @OperationAudit(module = "ticket", action = "update", targetType = "ticket", targetId = "#id", summary = "修改工单 #{id} 优先级")
    @PutMapping("/{id}/priority")
    public Result<TicketVO> updatePriority(@PathVariable String id, @RequestBody UpdateTicketPriorityRequest request) {
        return Result.ok(ticketService.updatePriority(id, request.priority()));
    }

    /** 修改工单分类（AI 分诊一键应用 / 手工归类）。 */
    @OperationAudit(module = "ticket", action = "update", targetType = "ticket", targetId = "#id", summary = "修改工单 #{id} 分类")
    @PutMapping("/{id}/category")
    public Result<TicketVO> updateCategory(@PathVariable String id, @RequestBody UpdateTicketCategoryRequest request) {
        return Result.ok(ticketService.updateCategory(id, request.category()));
    }

    /** 代录客户满意度（1-5 分 + 选填评语）。 */
    @OperationAudit(module = "ticket", action = "update", targetType = "ticket", targetId = "#id", summary = "工单 #{id} 录入 CSAT")
    @PostMapping("/{id}/csat")
    public Result<TicketVO> csat(@PathVariable String id, @RequestBody CsatRequest request) {
        return Result.ok(ticketService.csat(id, request.score(), request.comment()));
    }

    /** 查询工单的关联列表（双向可见）。 */
    @GetMapping("/{id}/relations")
    public Result<List<RelatedTicketVO>> listRelations(@PathVariable String id) {
        return Result.ok(ticketService.listRelations(id));
    }

    /** 新增工单关联：relatedTicketId 为对方工单号（数字 id），relationType 默认 related。 */
    @OperationAudit(module = "ticket", action = "update", targetType = "ticket", targetId = "#id", summary = "工单 #{id} 新增关联")
    @PostMapping("/{id}/relations")
    public Result<List<RelatedTicketVO>> addRelation(@PathVariable String id,
                                                     @RequestBody TicketRelationRequest request) {
        return Result.ok(ticketService.addRelation(id, request.relatedTicketId(), request.relationType()));
    }

    /** 取消工单关联。 */
    @OperationAudit(module = "ticket", action = "delete", targetType = "ticket", targetId = "#id", summary = "工单 #{id} 取消关联")
    @DeleteMapping("/{id}/relations/{relationId}")
    public Result<List<RelatedTicketVO>> removeRelation(@PathVariable String id,
                                                        @PathVariable Long relationId) {
        return Result.ok(ticketService.removeRelation(id, relationId));
    }

    /** 追加消息（客户回复或内部备注）。 */
    @OperationAudit(module = "ticket", action = "reply", targetType = "ticket", targetId = "#id", summary = "工单 #{id} 追加消息")
    @PostMapping("/{id}/messages")
    public Result<TicketVO> addMessage(@PathVariable String id, @RequestBody AddTicketMessageRequest request) {
        return Result.ok(ticketService.addMessage(id, request.text(), request.internal(), request.attachmentIds()));
    }
}
