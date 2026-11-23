package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.common.JwtUtil;
import com.supportpilot.common.Role;
import com.supportpilot.common.UserContext;
import com.supportpilot.dto.CreateUserRequest;
import com.supportpilot.dto.LoginRequest;
import com.supportpilot.dto.LoginResponse;
import com.supportpilot.dto.RegisterRequest;
import com.supportpilot.dto.UserVO;
import com.supportpilot.entity.User;
import com.supportpilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 用户与鉴权业务：登录签发 JWT、当前用户、用户 CRUD。
 * 密码使用 BCrypt 哈希存储与校验；管理操作（增删改他人、改角色）需 admin 角色。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final OperationLogService operationLogService;
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    public LoginResponse login(LoginRequest request) {
        User user;
        try {
            user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new BusinessException(401, "用户名或密码错误"));
            if (!ENCODER.matches(request.getPassword(), user.getPassword())) {
                throw new BusinessException(401, "用户名或密码错误");
            }
        } catch (BusinessException e) {
            // UserContext 此时尚未建立，显式传入尝试用户名；失败也记录便于安全审计
            operationLogService.recordLoginFailure(request.getUsername(), e.getMessage());
            throw e;
        }
        operationLogService.recordLoginSuccess(user);
        String token = jwtUtil.generateToken(
                user.getId(), user.getUsername(), user.getName(), user.getTenantId(), user.getRole(),
                user.getTeamId(), user.getDataScope());
        return new LoginResponse(token, toVO(user));
    }

    /**
     * 自助注册：创建普通支持工程师（support 角色、默认租户），注册成功即签发 token 返回，
     * 前端拿到后可直接进入登录态。不允许通过该接口注册 admin。
     */
    public LoginResponse register(RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            operationLogService.recordRegisterFailure(request.getUsername(), "用户名已存在");
            throw BusinessException.badRequest("用户名已存在");
        }
        User user = new User();
        long nextId = userRepository.findTopByOrderByIdDesc().map(u -> u.getId() + 1).orElse(1L);
        user.setId(nextId);
        user.setUsername(request.getUsername());
        user.setPassword(ENCODER.encode(request.getPassword()));
        user.setName(request.getName());
        user.setRole(Role.ENGINEER);
        user.setDataScope(Role.defaultScope(Role.ENGINEER));
        user.setTenantId(1L);
        user.setCreatedAt(Instant.now());
        User saved = userRepository.save(user);
        operationLogService.recordRegister(saved);
        String token = jwtUtil.generateToken(
                saved.getId(), saved.getUsername(), saved.getName(), saved.getTenantId(), saved.getRole(),
                saved.getTeamId(), saved.getDataScope());
        return new LoginResponse(token, toVO(saved));
    }

    public UserVO me() {
        UserContext.CurrentUser current = UserContext.get();
        if (current == null) {
            throw new BusinessException(401, "未登录");
        }
        return userRepository.findById(current.getId())
                .map(this::toVO)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
    }

    public List<UserVO> list() {
        return userRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt", "id")).stream().map(this::toVO).toList();
    }

    public UserVO create(CreateUserRequest request) {
        requireAdmin();
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw BusinessException.badRequest("用户名已存在");
        }
        String role = normalizeRole(request.getRole());
        User user = new User();
        long nextId = userRepository.findTopByOrderByIdDesc().map(u -> u.getId() + 1).orElse(1L);
        user.setId(nextId);
        user.setUsername(request.getUsername());
        user.setPassword(ENCODER.encode(request.getPassword()));
        user.setName(request.getName());
        user.setEmail(blank(request.getEmail()) ? null : request.getEmail().trim());
        user.setRole(role);
        user.setDataScope(normalizeScope(request.getDataScope(), role));
        user.setTeamId(request.getTeamId());
        user.setTenantId(request.getTenantId() == null ? 1L : request.getTenantId());
        user.setCreatedAt(Instant.now());
        return toVO(userRepository.save(user));
    }

    public UserVO update(Long id, CreateUserRequest request) {
        // 管理员可改任意人；普通用户只能改自己
        if (!UserContext.isAdmin()
                && (UserContext.get() == null || !id.equals(UserContext.get().getId()))) {
            throw new BusinessException(403, "无权限修改他人信息");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        if (request.getName() != null) {
            user.setName(request.getName());
        }
        if (request.getEmail() != null) {
            // 允许清空：显式传空字符串即清除邮件地址，回退「登录名@mail-domain」
            user.setEmail(blank(request.getEmail()) ? null : request.getEmail().trim());
        }
        if (!blank(request.getPassword())) {
            user.setPassword(ENCODER.encode(request.getPassword()));
        }
        if (UserContext.isAdmin()) {
            // 角色变更：若未显式指定 dataScope，则按新角色重置默认范围
            if (request.getRole() != null) {
                String newRole = normalizeRole(request.getRole());
                user.setRole(newRole);
                if (request.getDataScope() == null) {
                    user.setDataScope(Role.defaultScope(newRole));
                }
            }
            if (request.getTenantId() != null) {
                user.setTenantId(request.getTenantId());
            }
            if (request.getTeamId() != null) {
                user.setTeamId(request.getTeamId());
            }
            if (request.getDataScope() != null) {
                user.setDataScope(normalizeScope(request.getDataScope(), user.getRole()));
            }
        }
        return toVO(userRepository.save(user));
    }

    public void delete(Long id) {
        requireAdmin();
        if (!userRepository.existsById(id)) {
            throw BusinessException.notFound("用户不存在");
        }
        userRepository.deleteById(id);
    }

    private void requireAdmin() {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(403, "需要管理员权限");
        }
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /** 空 → ENGINEER；非法 → 400；否则归一化为大写。 */
    private String normalizeRole(String role) {
        if (blank(role)) {
            return Role.ENGINEER;
        }
        if (!Role.isValidRole(role)) {
            throw BusinessException.badRequest("非法角色：" + role);
        }
        return Role.normalize(role);
    }

    /** 空 → 按角色默认；非法 → 400；否则归一化为大写。 */
    private String normalizeScope(String scope, String role) {
        if (blank(scope)) {
            return Role.defaultScope(role);
        }
        if (!Role.isValidScope(scope)) {
            throw BusinessException.badRequest("非法数据范围：" + scope);
        }
        return scope.toUpperCase();
    }

    private UserVO toVO(User u) {
        UserVO v = new UserVO();
        v.setId(u.getId());
        v.setUsername(u.getUsername());
        v.setName(u.getName());
        v.setEmail(u.getEmail());
        v.setRole(u.getRole());
        v.setTenantId(u.getTenantId());
        v.setTeamId(u.getTeamId());
        v.setDataScope(u.getDataScope());
        return v;
    }
}
