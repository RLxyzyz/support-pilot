package com.supportpilot.service;

import com.supportpilot.entity.SlaCalendar;
import com.supportpilot.entity.SlaHoliday;
import com.supportpilot.repository.SlaCalendarRepository;
import com.supportpilot.repository.SlaHolidayRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SLA 工作日历计算（V2.1 FR-S06 / FR-S07）：
 * NATURAL 按自然时间；WORKTIME 跳过非工作日、节假日与非工作时段。
 */
@Service
public class SlaCalendarService {

    private static final long CACHE_TTL_MS = 60_000L;

    private final SlaCalendarRepository calendarRepository;
    private final SlaHolidayRepository holidayRepository;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public SlaCalendarService(SlaCalendarRepository calendarRepository,
                              SlaHolidayRepository holidayRepository) {
        this.calendarRepository = calendarRepository;
        this.holidayRepository = holidayRepository;
    }

    public record Spec(Set<DayOfWeek> workdays,
                       List<LocalTime[]> segments,
                       ZoneId zone,
                       Set<LocalDate> holidays) {
    }

    private record CacheEntry(Spec spec, long expireAt) {
    }

    /** 计算从 start 起追加 minutes 分钟后的到期时间。 */
    public Instant addMinutes(Instant start, long minutes, String calendarType, Long calendarId, Long tenantId) {
        if (start == null) {
            return null;
        }
        if (minutes <= 0) {
            return start;
        }
        Spec spec = loadSpec(calendarType, calendarId, tenantId);
        if (spec == null) {
            return start.plusSeconds(minutes * 60);
        }
        long remaining = minutes * 60;
        ZonedDateTime cursor = start.atZone(spec.zone());
        int guard = 0;
        while (remaining > 0 && guard++ < 3700) {
            LocalDate date = cursor.toLocalDate();
            if (isWorkday(spec, date)) {
                for (LocalTime[] seg : spec.segments()) {
                    ZonedDateTime segStart = date.atTime(seg[0]).atZone(spec.zone());
                    ZonedDateTime segEnd = date.atTime(seg[1]).atZone(spec.zone());
                    if (!cursor.isBefore(segEnd)) {
                        continue;
                    }
                    if (cursor.isBefore(segStart)) {
                        cursor = segStart;
                    }
                    long available = Duration.between(cursor, segEnd).getSeconds();
                    if (available <= 0) {
                        continue;
                    }
                    if (remaining <= available) {
                        return cursor.plusSeconds(remaining).toInstant();
                    }
                    remaining -= available;
                    cursor = segEnd;
                }
            }
            cursor = date.plusDays(1).atStartOfDay(spec.zone());
        }
        return cursor.toInstant();
    }

    /** 计算 [start, end] 内计入 SLA 的秒数（WORKTIME 只算工作时段）。 */
    public long consumedSeconds(Instant start, Instant end, String calendarType, Long calendarId, Long tenantId) {
        if (start == null || end == null || !end.isAfter(start)) {
            return 0L;
        }
        Spec spec = loadSpec(calendarType, calendarId, tenantId);
        if (spec == null) {
            return Duration.between(start, end).getSeconds();
        }
        ZonedDateTime from = start.atZone(spec.zone());
        ZonedDateTime to = end.atZone(spec.zone());
        long total = 0L;
        LocalDate date = from.toLocalDate();
        while (!date.isAfter(to.toLocalDate())) {
            if (isWorkday(spec, date)) {
                for (LocalTime[] seg : spec.segments()) {
                    ZonedDateTime segStart = date.atTime(seg[0]).atZone(spec.zone());
                    ZonedDateTime segEnd = date.atTime(seg[1]).atZone(spec.zone());
                    ZonedDateTime begin = from.isAfter(segStart) ? from : segStart;
                    ZonedDateTime finish = to.isBefore(segEnd) ? to : segEnd;
                    if (begin.isBefore(finish)) {
                        total += Duration.between(begin, finish).getSeconds();
                    }
                }
            }
            date = date.plusDays(1);
        }
        return total;
    }

    private boolean isWorkday(Spec spec, LocalDate date) {
        return spec.workdays().contains(date.getDayOfWeek()) && !spec.holidays().contains(date);
    }

    private Spec loadSpec(String calendarType, Long calendarId, Long tenantId) {
        if (!"WORKTIME".equalsIgnoreCase(calendarType) || calendarId == null) {
            return null;
        }
        long keyTenant = tenantId == null ? 0L : tenantId;
        String key = keyTenant + ":" + calendarId;
        CacheEntry entry = cache.get(key);
        long now = System.currentTimeMillis();
        if (entry != null && entry.expireAt() > now) {
            return entry.spec();
        }
        SlaCalendar calendar = calendarRepository.findByIdAndTenantId(calendarId, keyTenant).orElse(null);
        if (calendar == null || !Boolean.TRUE.equals(calendar.getEnabled())) {
            return null;
        }
        Spec spec = toSpec(calendar);
        cache.put(key, new CacheEntry(spec, now + CACHE_TTL_MS));
        return spec;
    }

    private Spec toSpec(SlaCalendar calendar) {
        Set<DayOfWeek> workdays = new HashSet<>();
        if (calendar.getWorkdays() != null) {
            for (String part : calendar.getWorkdays().split(",")) {
                try {
                    int v = Integer.parseInt(part.trim());
                    if (v >= 1 && v <= 7) {
                        workdays.add(DayOfWeek.of(v));
                    }
                } catch (NumberFormatException ignored) {
                    // 非法配置忽略，保持默认工作日
                }
            }
        }
        if (workdays.isEmpty()) {
            workdays.addAll(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        }

        List<LocalTime[]> segments = new ArrayList<>();
        if (calendar.getSegments() != null) {
            for (String part : calendar.getSegments().split(",")) {
                String[] range = part.trim().split("-");
                if (range.length != 2) {
                    continue;
                }
                try {
                    LocalTime s = LocalTime.parse(range[0].trim());
                    LocalTime e = LocalTime.parse(range[1].trim());
                    if (s.isBefore(e)) {
                        segments.add(new LocalTime[]{s, e});
                    }
                } catch (Exception ignored) {
                    // 非法时段忽略
                }
            }
        }
        if (segments.isEmpty()) {
            segments.add(new LocalTime[]{LocalTime.of(9, 0), LocalTime.of(18, 0)});
        }
        segments.sort(Comparator.comparing(a -> a[0]));

        ZoneId zone;
        try {
            zone = ZoneId.of(calendar.getTimezone() == null ? "Asia/Shanghai" : calendar.getTimezone());
        } catch (Exception e) {
            zone = ZoneId.of("Asia/Shanghai");
        }

        Set<LocalDate> holidays = new HashSet<>();
        for (SlaHoliday h : holidayRepository.findByCalendarIdOrderByHolidayDateAsc(calendar.getId())) {
            holidays.add(h.getHolidayDate());
        }
        return new Spec(workdays, segments, zone, holidays);
    }

    /** 配置变更后失效缓存。 */
    public void evict(Long calendarId) {
        cache.keySet().removeIf(k -> k.endsWith(":" + calendarId));
    }
}
