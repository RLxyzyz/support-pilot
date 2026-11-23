package com.supportpilot.common.audit;

import com.supportpilot.entity.Draft;
import com.supportpilot.repository.DraftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DraftSnapshotProvider implements AuditSnapshotProvider {

    private final DraftRepository draftRepository;

    @Override
    public String targetType() {
        return "draft";
    }

    @Override
    public Object load(String targetId) {
        if (targetId == null || targetId.isBlank()) {
            return null;
        }
        try {
            Long id = Long.valueOf(targetId.replace("d_", "").replace("#", "").trim());
            Draft d = draftRepository.findById(id).orElse(null);
            if (d == null) {
                return null;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("ticketId", d.getTicketId());
            m.put("status", d.getStatus());
            m.put("cost", d.getCost());
            // 内容截断，避免快照过大
            String content = d.getContent();
            if (content != null && content.length() > 500) {
                content = content.substring(0, 500) + "...(truncated)";
            }
            m.put("content", content);
            return m;
        } catch (Exception e) {
            return null;
        }
    }
}
