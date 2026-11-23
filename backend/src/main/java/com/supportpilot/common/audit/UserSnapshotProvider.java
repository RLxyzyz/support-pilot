package com.supportpilot.common.audit;

import com.supportpilot.common.TimeFormat;
import com.supportpilot.entity.User;
import com.supportpilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class UserSnapshotProvider implements AuditSnapshotProvider {

    private final UserRepository userRepository;

    @Override
    public String targetType() {
        return "user";
    }

    @Override
    public Object load(String targetId) {
        if (targetId == null || targetId.isBlank()) {
            return null;
        }
        try {
            Long id = Long.valueOf(targetId.trim());
            User u = userRepository.findById(id).orElse(null);
            if (u == null) {
                return null;
            }
            // 安全：不包含 password
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("name", u.getName());
            m.put("role", u.getRole());
            m.put("tenantId", u.getTenantId());
            m.put("createdAt", TimeFormat.format(u.getCreatedAt()));
            return m;
        } catch (Exception e) {
            return null;
        }
    }
}
