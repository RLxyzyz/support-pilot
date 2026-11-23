package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.common.UserContext;
import com.supportpilot.dto.TeamRequest;
import com.supportpilot.dto.TeamVO;
import com.supportpilot.entity.Team;
import com.supportpilot.repository.TeamRepository;
import com.supportpilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 工作组服务（V2.0 EP2）。列表全员可读，写操作需管理员（@RequireRole 拦截器 + 服务层双保险）。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Shanghai"));

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    public List<TeamVO> list() {
        return teamRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt", "id")).stream()
                .map(this::toVO).toList();
    }

    public TeamVO get(Long id) {
        return toVO(findOrThrow(id));
    }

    @Transactional
    public TeamVO create(TeamRequest req) {
        requireAdmin();
        String name = req.getName().trim();
        if (name.isBlank()) {
            throw BusinessException.badRequest("工作组名称不能为空");
        }
        Team t = new Team();
        t.setName(name);
        t.setStatus("active");
        t.setTenantId(currentTenantId());
        t.setCreatedAt(Instant.now());
        return toVO(teamRepository.save(t));
    }

    @Transactional
    public TeamVO update(Long id, TeamRequest req) {
        requireAdmin();
        Team t = findOrThrow(id);
        String name = req.getName().trim();
        if (name.isBlank()) {
            throw BusinessException.badRequest("工作组名称不能为空");
        }
        t.setName(name);
        return toVO(teamRepository.save(t));
    }

    @Transactional
    public TeamVO disable(Long id) {
        requireAdmin();
        Team t = findOrThrow(id);
        t.setStatus("disabled");
        return toVO(teamRepository.save(t));
    }

    private Team findOrThrow(Long id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("工作组不存在"));
    }

    private Long currentTenantId() {
        UserContext.CurrentUser u = UserContext.get();
        return u == null || u.getTenantId() == null ? 1L : u.getTenantId();
    }

    private void requireAdmin() {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(403, "需要管理员权限");
        }
    }

    private TeamVO toVO(Team t) {
        TeamVO v = new TeamVO();
        v.setId(t.getId());
        v.setName(t.getName());
        v.setStatus(t.getStatus());
        v.setMemberCount(userRepository.countByTeamId(t.getId()));
        v.setCreatedAt(t.getCreatedAt() == null ? null : FMT.format(t.getCreatedAt()));
        return v;
    }
}
