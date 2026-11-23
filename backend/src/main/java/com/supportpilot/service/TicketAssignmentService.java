package com.supportpilot.service;

import com.supportpilot.common.Role;
import com.supportpilot.entity.User;
import com.supportpilot.repository.TicketRepository;
import com.supportpilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 新建工单自动派单：在支持人员中挑当前未结工单最少的人，实现按持单量均分。
 *
 * <p>候选人优先取工程师 / 主管；若这两类角色无人，则退回全体用户（知识库管理员除外）。
 * 有同租户候选人时只在同租户内分配，避免跨租户派单。
 */
@Service
@RequiredArgsConstructor
public class TicketAssignmentService {

    /** 终态：已解决 / 已升级不再占用处理人的持单量。 */
    private static final List<String> CLOSED_STATUSES = List.of("已解决", "已升级");
    /** 首选支持人员角色。 */
    private static final List<String> PRIMARY_ROLES = List.of(Role.ENGINEER, Role.MANAGER);

    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;

    /**
     * 选取当前未结工单数最少的处理人，并列时取 id 最小者；无可分配人员返回 null。
     *
     * @param tenantId 工单所属租户，null 表示不限租户
     */
    @Transactional(readOnly = true)
    public Long pickLeastLoaded(Long tenantId) {
        List<User> users = userRepository.findAll();
        List<User> candidates = users.stream()
                .filter(u -> u.getId() != null && PRIMARY_ROLES.contains(Role.normalize(u.getRole())))
                .toList();
        if (candidates.isEmpty()) {
            candidates = users.stream()
                    .filter(u -> u.getId() != null && !Role.KB_ADMIN.equals(Role.normalize(u.getRole())))
                    .toList();
        }
        if (tenantId != null) {
            List<User> sameTenant = candidates.stream()
                    .filter(u -> tenantId.equals(u.getTenantId()))
                    .toList();
            if (!sameTenant.isEmpty()) {
                candidates = sameTenant;
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }

        Map<Long, Long> loads = new HashMap<>();
        for (Object[] row : ticketRepository.countOpenByAssignee(CLOSED_STATUSES)) {
            loads.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }

        User best = null;
        long bestLoad = Long.MAX_VALUE;
        for (User u : candidates) {
            long load = loads.getOrDefault(u.getId(), 0L);
            if (load < bestLoad || (load == bestLoad && best != null && u.getId() < best.getId())) {
                best = u;
                bestLoad = load;
            }
        }
        return best == null ? null : best.getId();
    }
}
