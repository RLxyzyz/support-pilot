package com.supportpilot.common.audit;

import com.supportpilot.entity.KbDoc;
import com.supportpilot.repository.KbDocRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class KbDocSnapshotProvider implements AuditSnapshotProvider {

    private final KbDocRepository kbDocRepository;

    @Override
    public String targetType() {
        return "kb_doc";
    }

    @Override
    public Object load(String targetId) {
        if (targetId == null || targetId.isBlank()) {
            return null;
        }
        try {
            Long id = Long.valueOf(targetId.trim());
            KbDoc d = kbDocRepository.findById(id).orElse(null);
            if (d == null) {
                return null;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("name", d.getName());
            m.put("path", d.getPath());
            m.put("productLine", d.getProductLine());
            m.put("tenantId", d.getTenantId());
            m.put("status", d.getStatus());
            m.put("chunks", d.getChunks());
            m.put("hits", d.getHits());
            return m;
        } catch (Exception e) {
            return null;
        }
    }
}
