package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.common.NotifyType;
import com.supportpilot.dto.NotifyRuleRequest;
import com.supportpilot.dto.NotifyRuleVO;
import com.supportpilot.entity.NotifyRule;
import com.supportpilot.repository.NotifyRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 通知规则服务（V2.0 EP3）：规则 CRUD + 内置默认规则幂等种子。
 * 规则不缓存，变更即时生效（每次事件触发时实时查库）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyRuleService {

    private final NotifyRuleRepository notifyRuleRepository;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    public List<NotifyRuleVO> list() {
        return notifyRuleRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt", "id")).stream()
                .map(this::toVO).toList();
    }

    public NotifyRuleVO create(NotifyRuleRequest request) {
        String eventType = normalizeEventType(request.getEventType());
        if (notifyRuleRepository.existsByEventType(eventType)) {
            throw BusinessException.badRequest("该事件类型已存在规则，请直接编辑");
        }
        NotifyRule rule = new NotifyRule();
        rule.setEventType(eventType);
        applyRequest(rule, request);
        rule.setCreatedAt(Instant.now());
        return toVO(notifyRuleRepository.save(rule));
    }

    public NotifyRuleVO update(Long id, NotifyRuleRequest request) {
        NotifyRule rule = notifyRuleRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("通知规则不存在"));
        String eventType = normalizeEventType(request.getEventType());
        if (!eventType.equals(rule.getEventType()) && notifyRuleRepository.existsByEventType(eventType)) {
            throw BusinessException.badRequest("该事件类型已存在规则");
        }
        rule.setEventType(eventType);
        applyRequest(rule, request);
        return toVO(notifyRuleRepository.save(rule));
    }

    public void delete(Long id) {
        if (!notifyRuleRepository.existsById(id)) {
            throw BusinessException.notFound("通知规则不存在");
        }
        notifyRuleRepository.deleteById(id);
    }

    /**
     * 幂等补齐内置默认规则：分派 / 转派 → 被分派人；SLA 预警 / 超时 → 处理人 + 主管。
     * 已存在的规则不覆盖（保留管理员的自定义修改）。
     */
    @Transactional
    public void seedDefaults() {
        seed(NotifyType.TICKET_ASSIGNED, "工单分派通知", NotifyType.RECEIVER_ASSIGNEE);
        seed(NotifyType.TICKET_TRANSFERRED, "工单转派通知", NotifyType.RECEIVER_ASSIGNEE);
        seed(NotifyType.SLA_WARNING, "SLA 预警通知",
                NotifyType.RECEIVER_ASSIGNEE + "," + NotifyType.RECEIVER_MANAGER);
        seed(NotifyType.SLA_BREACH, "SLA 超时通知",
                NotifyType.RECEIVER_ASSIGNEE + "," + NotifyType.RECEIVER_MANAGER);
    }

    private void seed(String eventType, String name, String receivers) {
        if (notifyRuleRepository.existsByEventType(eventType)) {
            return;
        }
        NotifyRule rule = new NotifyRule();
        rule.setEventType(eventType);
        rule.setName(name);
        rule.setReceivers(receivers);
        rule.setInAppEnabled(true);
        rule.setEmailEnabled(true);
        rule.setEnabled(true);
        rule.setCreatedAt(Instant.now());
        notifyRuleRepository.save(rule);
        log.info("[通知规则] 已补齐默认规则：{}（接收人 {}）", eventType, receivers);
    }

    private void applyRequest(NotifyRule rule, NotifyRuleRequest request) {
        rule.setName(request.getName());
        rule.setConditionJson(request.getConditionJson());
        rule.setReceivers(request.getReceivers().trim());
        rule.setInAppEnabled(request.getInAppEnabled() == null || request.getInAppEnabled());
        rule.setEmailEnabled(Boolean.TRUE.equals(request.getEmailEnabled()));
        rule.setEnabled(request.getEnabled() == null || request.getEnabled());
    }

    private String normalizeEventType(String eventType) {
        String v = eventType == null ? "" : eventType.trim().toUpperCase();
        if (!NotifyType.LABELS.containsKey(v)) {
            throw BusinessException.badRequest("未知事件类型：" + eventType);
        }
        return v;
    }

    private NotifyRuleVO toVO(NotifyRule rule) {
        NotifyRuleVO v = new NotifyRuleVO();
        v.setId(rule.getId());
        v.setTenantId(rule.getTenantId());
        v.setEventType(rule.getEventType());
        v.setEventLabel(NotifyType.label(rule.getEventType()));
        v.setName(rule.getName());
        v.setConditionJson(rule.getConditionJson());
        v.setReceivers(rule.getReceivers());
        v.setInAppEnabled(rule.getInAppEnabled());
        v.setEmailEnabled(rule.getEmailEnabled());
        v.setEnabled(rule.getEnabled());
        v.setCreatedAt(rule.getCreatedAt() == null ? null : TIME_FMT.format(rule.getCreatedAt()));
        return v;
    }
}
