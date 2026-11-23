package com.supportpilot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportpilot.common.Role;
import com.supportpilot.common.UserContext;
import com.supportpilot.common.security.RequireRole;
import com.supportpilot.service.OperationLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * RoleInterceptor 集中鉴权单测（TR-6.1 / TR-6.3）。
 */
@ExtendWith(MockitoExtension.class)
class RoleInterceptorTest {

    @Mock
    OperationLogService operationLogService;
    private RoleInterceptor interceptor;

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    static class TestController {
        @RequireRole(Role.ADMIN)
        public void adminOnly() {
        }

        public void open() {
        }
    }

    @BeforeEach
    void setUp() {
        interceptor = new RoleInterceptor(operationLogService, new ObjectMapper());
        ReflectionTestUtils.setField(interceptor, "rbacEnabled", true);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private HandlerMethod handler(String methodName) throws NoSuchMethodException {
        Method m = TestController.class.getMethod(methodName);
        return new HandlerMethod(new TestController(), m);
    }

    private void setUser(String role) {
        UserContext.set(new UserContext.CurrentUser(2L, "u", "U", 1L, role, 7L, null));
    }

    @Test
    void unauthorizedRoleReturns403AndWritesAudit() throws Exception {
        setUser(Role.ENGINEER);
        request.setMethod("DELETE");
        request.setRequestURI("/api/users/9");

        boolean proceed = interceptor.preHandle(request, response, handler("adminOnly"));

        assertFalse(proceed);
        assertEquals(403, response.getStatus());
        verify(operationLogService, times(1)).recordAccessDenied(anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void authorizedRolePassesWithoutAudit() throws Exception {
        setUser(Role.ADMIN);
        request.setMethod("DELETE");
        request.setRequestURI("/api/users/9");

        boolean proceed = interceptor.preHandle(request, response, handler("adminOnly"));

        assertTrue(proceed);
        assertEquals(200, response.getStatus());
        verify(operationLogService, never()).recordAccessDenied(anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void unannotatedEndpointAlwaysPasses() throws Exception {
        setUser(Role.ENGINEER);
        boolean proceed = interceptor.preHandle(request, response, handler("open"));
        assertTrue(proceed);
        verify(operationLogService, never()).recordAccessDenied(anyString(), anyString(), anyString(), any(), anyString());
    }

    /** PRD 13.1 回滚方案：开关关闭后退回旧鉴权，角色校验放行。 */
    @Test
    void rbacDisabledPassesEvenForAdminOnlyEndpoint() throws Exception {
        ReflectionTestUtils.setField(interceptor, "rbacEnabled", false);
        setUser(Role.ENGINEER);

        boolean proceed = interceptor.preHandle(request, response, handler("adminOnly"));

        assertTrue(proceed);
        verify(operationLogService, never()).recordAccessDenied(anyString(), anyString(), anyString(), any(), anyString());
    }
}
