package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.common.JwtUtil;
import com.supportpilot.common.Role;
import com.supportpilot.common.UserContext;
import com.supportpilot.dto.CreateUserRequest;
import com.supportpilot.dto.UserVO;
import com.supportpilot.entity.User;
import com.supportpilot.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * UserService 角色归一化与数据范围默认值（TR-5.2）。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    JwtUtil jwtUtil;
    @Mock
    OperationLogService operationLogService;
    @InjectMocks
    UserService userService;

    @BeforeEach
    void setUp() {
        UserContext.set(new UserContext.CurrentUser(1L, "admin", "管理员", 1L, Role.ADMIN, null, Role.SCOPE_ALL));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private CreateUserRequest req(String role, String scope) {
        CreateUserRequest r = new CreateUserRequest();
        r.setUsername("u");
        r.setPassword("p");
        r.setName("U");
        r.setRole(role);
        r.setDataScope(scope);
        return r;
    }

    @Test
    void createEngineerDefaultsToTeamScope() {
        when(userRepository.findByUsername("u")).thenReturn(Optional.empty());
        when(userRepository.findTopByOrderByIdDesc()).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserVO vo = userService.create(req(null, null));
        assertEquals(Role.ENGINEER, vo.getRole());
        assertEquals(Role.SCOPE_TEAM, vo.getDataScope());
    }

    @Test
    void createAdminDefaultsToAllScopeAndNormalizesCase() {
        when(userRepository.findByUsername("u")).thenReturn(Optional.empty());
        when(userRepository.findTopByOrderByIdDesc()).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserVO vo = userService.create(req("admin", null));
        assertEquals(Role.ADMIN, vo.getRole());
        assertEquals(Role.SCOPE_ALL, vo.getDataScope());
    }

    @Test
    void createWithExplicitScopeOverridesDefault() {
        when(userRepository.findByUsername("u")).thenReturn(Optional.empty());
        when(userRepository.findTopByOrderByIdDesc()).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserVO vo = userService.create(req(Role.ENGINEER, Role.SCOPE_SELF));
        assertEquals(Role.SCOPE_SELF, vo.getDataScope());
    }

    @Test
    void createRejectsInvalidRole() {
        when(userRepository.findByUsername("u")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.create(req("superuser", null)));
        assertEquals(400, ex.getCode());
    }

    @Test
    void createRejectsInvalidScope() {
        when(userRepository.findByUsername("u")).thenReturn(Optional.empty());
        when(userRepository.findTopByOrderByIdDesc()).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.create(req(Role.ENGINEER, "GLOBAL")));
        assertEquals(400, ex.getCode());
    }

    @Test
    void updateRoleResetsDataScopeToDefaultWhenScopeOmitted() {
        User existing = new User();
        existing.setId(2L);
        existing.setRole(Role.ENGINEER);
        existing.setDataScope(Role.SCOPE_TEAM);
        when(userRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateUserRequest r = req(null, null);
        r.setRole(Role.ADMIN);
        UserVO vo = userService.update(2L, r);

        assertEquals(Role.ADMIN, vo.getRole());
        assertEquals(Role.SCOPE_ALL, vo.getDataScope());
    }

    @Test
    void nonAdminCannotUpdateOthers() {
        UserContext.set(new UserContext.CurrentUser(2L, "eng", "工程师", 1L, Role.ENGINEER, 7L, Role.SCOPE_TEAM));

        CreateUserRequest r = req(null, null);
        r.setName("hacked");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.update(99L, r));
        assertEquals(403, ex.getCode());
    }
}
