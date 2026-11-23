package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.common.TenantContext;
import com.supportpilot.common.audit.AuditContext;
import com.supportpilot.common.UserContext;
import com.supportpilot.dto.SlaCalendarRequest;
import com.supportpilot.dto.SlaHolidayRequest;
import com.supportpilot.entity.SlaCalendar;
import com.supportpilot.entity.SlaHoliday;
import com.supportpilot.repository.SlaCalendarRepository;
import com.supportpilot.repository.SlaHolidayRepository;
import com.supportpilot.repository.SlaPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 计时日历与节假日维护（V2.1 M1 FR-S06）：配置变更后立即失效 SLA 计算缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlaConfigService {

    private final SlaCalendarRepository calendarRepository;
    private final SlaHolidayRepository holidayRepository;
    private final SlaPolicyRepository policyRepository;
    private final SlaCalendarService calendarService;
    private final OperationLogService operationLogService;

    public List<SlaCalendar> listCalendars() {
        return calendarRepository.findByTenantIdOrderByIdAsc(tenantKey());
    }

    @Transactional
    public SlaCalendar createCalendar(SlaCalendarRequest req) {
        SlaCalendar c = new SlaCalendar();
        c.setTenantId(tenantKey());
        c.setCreatedAt(Instant.now());
        apply(c, req);
        SlaCalendar saved = calendarRepository.save(c);
        audit("create", "创建计时日历：" + saved.getName());
        return saved;
    }

    @Transactional
    public SlaCalendar updateCalendar(Long id, SlaCalendarRequest req) {
        SlaCalendar c = calendarRepository.findByIdAndTenantId(id, tenantKey())
                .orElseThrow(() -> BusinessException.notFound("日历不存在"));
        apply(c, req);
        SlaCalendar saved = calendarRepository.save(c);
        calendarService.evict(id);
        audit("update", "更新计时日历：" + saved.getName());
        return saved;
    }

    @Transactional
    public void deleteCalendar(Long id) {
        SlaCalendar c = calendarRepository.findByIdAndTenantId(id, tenantKey())
                .orElseThrow(() -> BusinessException.notFound("日历不存在"));
        if (policyRepository.countByTenantIdAndCalendarId(tenantKey(), id) > 0) {
            throw BusinessException.badRequest("该日历已被 SLA 策略引用，请先调整策略");
        }
        holidayRepository.deleteByCalendarId(id);
        calendarRepository.delete(c);
        calendarService.evict(id);
        audit("delete", "删除计时日历：" + c.getName());
    }

    public List<SlaHoliday> listHolidays(Long calendarId) {
        requireCalendar(calendarId);
        return holidayRepository.findByCalendarIdOrderByHolidayDateAsc(calendarId);
    }

    @Transactional
    public SlaHoliday addHoliday(Long calendarId, SlaHolidayRequest req) {
        requireCalendar(calendarId);
        LocalDate date = parseDate(req.holidayDate());
        if (holidayRepository.existsByCalendarIdAndHolidayDate(calendarId, date)) {
            throw BusinessException.badRequest("该日期已存在节假日配置");
        }
        SlaHoliday h = new SlaHoliday();
        h.setCalendarId(calendarId);
        h.setHolidayDate(date);
        h.setName(req.name());
        SlaHoliday saved = holidayRepository.save(h);
        calendarService.evict(calendarId);
        audit("update", "新增节假日：" + date + (req.name() == null ? "" : " " + req.name()));
        return saved;
    }

    @Transactional
    public void removeHoliday(Long calendarId, String date) {
        requireCalendar(calendarId);
        LocalDate d = parseDate(date);
        holidayRepository.deleteOne(calendarId, d);
        calendarService.evict(calendarId);
        audit("update", "删除节假日：" + d);
    }

    private void apply(SlaCalendar c, SlaCalendarRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()) {
            throw BusinessException.badRequest("日历名称不能为空");
        }
        String workdays = req.workdays() == null || req.workdays().isBlank() ? "1,2,3,4,5" : req.workdays().trim();
        validateWorkdays(workdays);
        String segments = req.segments() == null || req.segments().isBlank()
                ? "09:00-18:00" : req.segments().trim();
        validateSegments(segments);
        String timezone = req.timezone() == null || req.timezone().isBlank()
                ? "Asia/Shanghai" : req.timezone().trim();
        try {
            ZoneId.of(timezone);
        } catch (Exception e) {
            throw BusinessException.badRequest("非法时区：" + timezone);
        }
        c.setName(req.name().trim());
        c.setWorkdays(workdays);
        c.setSegments(segments);
        c.setTimezone(timezone);
        c.setEnabled(req.enabled() == null || req.enabled());
        c.setUpdatedAt(Instant.now());
    }

    private void validateWorkdays(String workdays) {
        for (String part : workdays.split(",")) {
            try {
                int v = Integer.parseInt(part.trim());
                if (v < 1 || v > 7) {
                    throw BusinessException.badRequest("工作日取值必须是 1–7");
                }
            } catch (NumberFormatException e) {
                throw BusinessException.badRequest("工作日格式非法，应为 1,2,3 这种位图");
            }
        }
    }

    private void validateSegments(String segments) {
        for (String part : segments.split(",")) {
            String[] range = part.trim().split("-");
            if (range.length != 2) {
                throw BusinessException.badRequest("工作时段格式非法，应为 09:00-18:00");
            }
            try {
                LocalTime s = LocalTime.parse(range[0].trim());
                LocalTime e = LocalTime.parse(range[1].trim());
                if (!s.isBefore(e)) {
                    throw BusinessException.badRequest("工作时段开始时间必须早于结束时间");
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw BusinessException.badRequest("工作时段格式非法，应为 09:00-18:00");
            }
        }
    }

    private SlaCalendar requireCalendar(Long id) {
        return calendarRepository.findByIdAndTenantId(id, tenantKey())
                .orElseThrow(() -> BusinessException.notFound("日历不存在"));
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text);
        } catch (Exception e) {
            throw BusinessException.badRequest("日期格式应为 yyyy-MM-dd");
        }
    }

    private long tenantKey() {
        return SlaPolicyService.tenantKey(TenantContext.get());
    }

    private void audit(String action, String summary) {
        UserContext.CurrentUser u = UserContext.get();
        AuditContext ctx = new AuditContext(
                u == null ? null : u.getId(),
                u == null ? null : u.getName(),
                u == null ? null : u.getUsername(),
                TenantContext.get(), "sla", action, "sla_calendar", null, summary);
        operationLogService.record(ctx, null, null, null, true, null, 0);
    }
}
