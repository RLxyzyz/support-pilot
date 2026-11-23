package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.common.DisplayUtil;
import com.supportpilot.common.NotifyType;
import com.supportpilot.common.TenantContext;
import com.supportpilot.common.TimeFormat;
import com.supportpilot.entity.StormRecord;
import com.supportpilot.entity.Ticket;
import com.supportpilot.entity.TicketRelation;
import com.supportpilot.repository.StormRecordRepository;
import com.supportpilot.repository.TenantRepository;
import com.supportpilot.repository.TicketRelationRepository;
import com.supportpilot.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * M2 故障风暴识别：每分钟统计近 30 分钟同分类新建工单数，
 * 超过阈值即生成风暴记录并向主管告警；支持升级为事件单、恢复与忽略。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StormService {

    private static final int WINDOW_MINUTES = 30;
    private static final int THRESHOLD = 5;

    private final StormRecordRepository stormRepository;
    private final TicketRepository ticketRepository;
    private final TicketRelationRepository ticketRelationRepository;
    private final TenantRepository tenantRepository;
    private final SmartAlertService smartAlertService;

    @Value("${app.tenant.default-id:1}")
    private Long defaultTenantId;

    /** 每分钟扫描一次（FR-M2-01）。 */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void scan() {
        Instant since = Instant.now().minus(WINDOW_MINUTES, ChronoUnit.MINUTES);
        List<Object[]> rows = ticketRepository.countByCategorySince(null, since);
        for (Object[] row : rows) {
            String category = row[0] == null ? "未分类" : row[0].toString();
            int count = row[1] == null ? 0 : ((Number) row[1]).intValue();
            if (count < THRESHOLD) {
                continue;
            }
            Ticket latest = ticketRepository.findFirstByCategoryOrderByIdDesc(category).orElse(null);
            Optional<StormRecord> existing =
                    stormRepository.findFirstByStormKeyAndStatus(category, StormRecord.STATUS_ACTIVE);
            if (existing.isPresent()) {
                StormRecord r = existing.get();
                r.setCount(count);
                r.setLastTicketId(latest == null ? r.getLastTicketId() : latest.getId());
                r.setUpdatedAt(Instant.now());
                stormRepository.save(r);
                continue;
            }
            StormRecord r = new StormRecord();
            r.setStormKey(category);
            r.setCategory(category);
            r.setGroupId(category);
            r.setCount(count);
            r.setWindowStart(since);
            r.setWindowMinutes(WINDOW_MINUTES);
            r.setThreshold(THRESHOLD);
            r.setStatus(StormRecord.STATUS_ACTIVE);
            r.setLastTicketId(latest == null ? null : latest.getId());
            r.setCreatedAt(Instant.now());
            r.setUpdatedAt(Instant.now());
            stormRepository.save(r);
            log.warn("[故障风暴] 分类「{}」近 {} 分钟新增 {} 单，已触发告警", category, WINDOW_MINUTES, count);
            smartAlertService.notifyManagers(null, NotifyType.STORM_ALERT,
                    "【故障风暴】" + category + " 工单激增",
                    "分类「" + category + "」近 " + WINDOW_MINUTES + " 分钟新增 " + count
                            + " 单，请尽快确认是否为同一故障。",
                    "storm", String.valueOf(r.getId()));
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> active() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (StormRecord r : stormRepository.findByStatusOrderByIdDesc(StormRecord.STATUS_ACTIVE)) {
            list.add(toVO(r));
        }
        return list;
    }

    /** 升级为事件单：创建分类=事件 的工单，并批量关联组内工单。 */
    @Transactional
    public Map<String, Object> upgrade(Long stormId) {
        StormRecord r = load(stormId);
        if (!StormRecord.STATUS_ACTIVE.equals(r.getStatus())) {
            throw BusinessException.badRequest("该风暴已结束，无法升级");
        }
        Ticket event = createEventTicket(r);
        r.setEventTicketId(event.getId());
        r.setGroupId(String.valueOf(event.getId()));
        r.setUpdatedAt(Instant.now());
        stormRepository.save(r);

        Instant since = r.getWindowStart() == null
                ? Instant.now().minus(WINDOW_MINUTES, ChronoUnit.MINUTES) : r.getWindowStart();
        int related = 0;
        for (Ticket t : ticketRepository.findRecentForSimilarity(null, since)) {
            if (event.getId().equals(t.getId()) || !r.getCategory().equals(t.getCategory())) {
                continue;
            }
            if (!ticketRelationRepository.existsByTicketIdAndRelatedTicketId(event.getId(), t.getId())) {
                TicketRelation rel = new TicketRelation();
                rel.setTicketId(event.getId());
                rel.setRelatedTicketId(t.getId());
                rel.setRelationType("storm");
                rel.setCreatedAt(Instant.now());
                ticketRelationRepository.save(rel);
                related++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventTicketId", "#" + event.getId());
        result.put("related", related);
        return result;
    }

    /** 恢复：风暴关闭，事件单置为已解决。 */
    @Transactional
    public Map<String, Object> recover(Long stormId) {
        StormRecord r = load(stormId);
        r.setStatus(StormRecord.STATUS_RECOVERED);
        r.setUpdatedAt(Instant.now());
        stormRepository.save(r);
        if (r.getEventTicketId() != null) {
            ticketRepository.findById(r.getEventTicketId()).ifPresent(t -> {
                t.setStatus("已解决");
                t.setStatusClass(DisplayUtil.statusClass("已解决"));
                t.setResolvedAt(Instant.now());
                t.setUpdateTime("刚刚");
                ticketRepository.save(t);
            });
        }
        return Map.of("recovered", true, "stormId", stormId);
    }

    /** 忽略：人工确认后关闭风暴。 */
    @Transactional
    public Map<String, Object> dismiss(Long stormId) {
        StormRecord r = load(stormId);
        r.setStatus(StormRecord.STATUS_DISMISSED);
        r.setUpdatedAt(Instant.now());
        stormRepository.save(r);
        return Map.of("dismissed", true, "stormId", stormId);
    }

    private StormRecord load(Long id) {
        return stormRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("风暴记录不存在"));
    }

    private Ticket createEventTicket(StormRecord r) {
        Long nextId = ticketRepository.findTopByOrderByIdDesc()
                .map(t -> t.getId() + 1).orElse(41000L);
        Ticket t = new Ticket();
        t.setId(nextId);
        Long tenantId = TenantContext.get() == null ? defaultTenantId : TenantContext.get();
        tenantRepository.findById(tenantId).ifPresent(t::setTenant);
        t.setTitle("【故障风暴】" + r.getCategory() + " 工单激增（" + r.getCount() + " 单/" + r.getWindowMinutes() + " 分钟）");
        t.setBody("系统检测到分类「" + r.getCategory() + "」在 " + r.getWindowMinutes()
                + " 分钟内新增 " + r.getCount() + " 单，可能为同一故障。请统一处置并同步进展。");
        t.setCode("");
        t.setPriority("P1");
        t.setPriorityClass(DisplayUtil.priorityClass("P1"));
        t.setStatus("处理中");
        t.setStatusClass(DisplayUtil.statusClass("处理中"));
        t.setCategory("事件");
        t.setSource("storm");
        t.setUpdateTime("刚刚");
        t.setCreatedAt(Instant.now());
        t.setSlaPausedSeconds(0L);
        ticketRepository.save(t);
        return t;
    }

    private Map<String, Object> toVO(StormRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("stormKey", r.getStormKey());
        m.put("category", r.getCategory());
        m.put("count", r.getCount());
        m.put("windowMinutes", r.getWindowMinutes());
        m.put("threshold", r.getThreshold());
        m.put("status", r.getStatus());
        m.put("eventTicketId", r.getEventTicketId() == null ? null : "#" + r.getEventTicketId());
        m.put("createdAt", TimeFormat.format(r.getCreatedAt()));
        return m;
    }
}
