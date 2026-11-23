package com.supportpilot.common.audit;

import com.supportpilot.common.TimeFormat;
import com.supportpilot.entity.Ticket;
import com.supportpilot.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TicketSnapshotProvider implements AuditSnapshotProvider {

    private final TicketRepository ticketRepository;

    @Override
    public String targetType() {
        return "ticket";
    }

    @Override
    public Object load(String targetId) {
        if (targetId == null || targetId.isBlank()) {
            return null;
        }
        try {
            Long id = Long.valueOf(targetId.replace("#", "").trim());
            Ticket t = ticketRepository.findById(id).orElse(null);
            if (t == null) {
                return null;
            }
            // 注意：tenant.getId() / product.getId() 取代理主键不触发懒加载；body/code 不纳入快照避免过大
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("title", t.getTitle());
            m.put("status", t.getStatus());
            m.put("priority", t.getPriority());
            m.put("category", t.getCategory());
            m.put("assigneeId", t.getAssigneeId());
            m.put("tenantId", t.getTenant() != null ? t.getTenant().getId() : null);
            m.put("productId", t.getProduct() != null ? t.getProduct().getId() : null);
            m.put("source", t.getSource());
            m.put("env", t.getEnv());
            m.put("createdAt", TimeFormat.format(t.getCreatedAt()));
            return m;
        } catch (Exception e) {
            return null;
        }
    }
}
