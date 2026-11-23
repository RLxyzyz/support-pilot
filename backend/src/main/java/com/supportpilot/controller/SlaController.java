package com.supportpilot.controller;

import com.supportpilot.common.Result;
import com.supportpilot.common.Role;
import com.supportpilot.common.audit.OperationAudit;
import com.supportpilot.common.security.RequireRole;
import com.supportpilot.dto.SlaCalendarRequest;
import com.supportpilot.dto.SlaHolidayRequest;
import com.supportpilot.dto.SlaPolicyRequest;
import com.supportpilot.entity.SlaCalendar;
import com.supportpilot.entity.SlaHoliday;
import com.supportpilot.entity.SlaPolicy;
import com.supportpilot.service.SlaConfigService;
import com.supportpilot.service.SlaPolicyService;
import lombok.RequiredArgsConstructor;
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

/**
 * SLA 策略与日历（V2.1 M1）：读接口 ADMIN/MANAGER/ENGINEER，写接口仅 ADMIN；
 * 重算 / 补算 / 模拟由管理员或主管触发。
 */
@RestController
@RequestMapping("/api/sla")
@RequiredArgsConstructor
public class SlaController {

    private final SlaPolicyService slaPolicyService;
    private final SlaConfigService slaConfigService;

    @RequireRole({Role.ADMIN, Role.MANAGER, Role.ENGINEER})
    @GetMapping("/policies")
    public Result<List<SlaPolicy>> policies() {
        return Result.ok(slaPolicyService.listAllTiers());
    }

    @RequireRole({Role.ADMIN})
    @OperationAudit(module = "sla", action = "update", targetType = "sla_policy", summary = "更新 SLA 策略")
    @PutMapping("/policies")
    public Result<SlaPolicy> upsertPolicy(@RequestBody SlaPolicyRequest request) {
        return Result.ok(slaPolicyService.upsert(request.tier(), request.priority(),
                request.firstResponseMinutes(), request.resolveMinutes(), request.calendarType(),
                request.calendarId(), request.warningThreshold(), request.enabled()));
    }

    @RequireRole({Role.ADMIN})
    @OperationAudit(module = "sla", action = "update", targetType = "ticket", summary = "重算未解决工单 SLA")
    @PostMapping("/recalculate")
    public Result<SlaPolicyService.RecalcResult> recalculate(@RequestParam(required = false) String reason) {
        return Result.ok(slaPolicyService.recalculateAll(reason == null ? "手动重算" : reason));
    }

    @RequireRole({Role.ADMIN})
    @OperationAudit(module = "sla", action = "update", targetType = "ticket", summary = "存量工单 SLA 补算")
    @PostMapping("/backfill")
    public Result<Map<String, Object>> backfill() {
        return Result.ok(Map.of("updated", slaPolicyService.backfill()));
    }

    @RequireRole({Role.ADMIN, Role.MANAGER})
    @GetMapping("/simulate")
    public Result<Map<String, Object>> simulate(@RequestParam String tier,
                                                @RequestParam String priority,
                                                @RequestParam(required = false) String start,
                                                @RequestParam(required = false) String now) {
        return Result.ok(slaPolicyService.simulate(tier, priority, parseInstant(start), parseInstant(now)));
    }

    // ---------------- 计时日历 ----------------

    @RequireRole({Role.ADMIN, Role.MANAGER, Role.ENGINEER})
    @GetMapping("/calendars")
    public Result<List<SlaCalendar>> calendars() {
        return Result.ok(slaConfigService.listCalendars());
    }

    @RequireRole({Role.ADMIN})
    @OperationAudit(module = "sla", action = "create", targetType = "sla_calendar", summary = "创建计时日历")
    @PostMapping("/calendars")
    public Result<SlaCalendar> createCalendar(@RequestBody SlaCalendarRequest request) {
        return Result.ok(slaConfigService.createCalendar(request));
    }

    @RequireRole({Role.ADMIN})
    @OperationAudit(module = "sla", action = "update", targetType = "sla_calendar", targetId = "#id", summary = "更新计时日历 #{id}")
    @PutMapping("/calendars/{id}")
    public Result<SlaCalendar> updateCalendar(@PathVariable Long id, @RequestBody SlaCalendarRequest request) {
        return Result.ok(slaConfigService.updateCalendar(id, request));
    }

    @RequireRole({Role.ADMIN})
    @OperationAudit(module = "sla", action = "delete", targetType = "sla_calendar", targetId = "#id", summary = "删除计时日历 #{id}")
    @DeleteMapping("/calendars/{id}")
    public Result<Void> deleteCalendar(@PathVariable Long id) {
        slaConfigService.deleteCalendar(id);
        return Result.ok();
    }

    @RequireRole({Role.ADMIN, Role.MANAGER, Role.ENGINEER})
    @GetMapping("/calendars/{id}/holidays")
    public Result<List<SlaHoliday>> holidays(@PathVariable Long id) {
        return Result.ok(slaConfigService.listHolidays(id));
    }

    @RequireRole({Role.ADMIN})
    @OperationAudit(module = "sla", action = "update", targetType = "sla_calendar", targetId = "#id", summary = "新增节假日 #{id}")
    @PostMapping("/calendars/{id}/holidays")
    public Result<SlaHoliday> addHoliday(@PathVariable Long id, @RequestBody SlaHolidayRequest request) {
        return Result.ok(slaConfigService.addHoliday(id, request));
    }

    @RequireRole({Role.ADMIN})
    @OperationAudit(module = "sla", action = "update", targetType = "sla_calendar", targetId = "#id", summary = "删除节假日 #{id}")
    @DeleteMapping("/calendars/{id}/holidays")
    public Result<Void> removeHoliday(@PathVariable Long id, @RequestParam String date) {
        slaConfigService.removeHoliday(id, date);
        return Result.ok();
    }

    private Instant parseInstant(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(text.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
