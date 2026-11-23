package com.supportpilot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportpilot.common.Result;
import com.supportpilot.common.UserContext;
import com.supportpilot.common.security.RequireRole;
import com.supportpilot.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 集中式 RBAC 鉴权拦截器（V2.0 EP2 / FR-A01）。
 *
 * <p>preHandle 读取方法（回落类）上的 {@link RequireRole}，命中则放行；否则写一条越权审计日志
 * 并直接返回 403，业务方法不会执行（业务零侵入）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleInterceptor implements HandlerInterceptor {

    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    /** RBAC 降级开关（PRD 13.1）：关闭后退回旧鉴权，所有接口只读放行。 */
    @org.springframework.beans.factory.annotation.Value("${app.rbac.enabled:true}")
    private boolean rbacEnabled;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!rbacEnabled) {
            return true;
        }
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        RequireRole ann = hm.getMethodAnnotation(RequireRole.class);
        if (ann == null) {
            ann = hm.getBeanType().getAnnotation(RequireRole.class);
        }
        if (ann == null || ann.value().length == 0) {
            return true;
        }
        if (UserContext.hasRole(ann.value())) {
            return true;
        }

        // 越权：审计 + 403
        String uri = request.getRequestURI();
        String method = request.getMethod();
        String module = deriveModule(uri);
        String required = String.join("/", ann.value());
        String msg = "越权访问 " + method + " " + uri + "（需要角色 " + required + "）";
        try {
            operationLogService.recordAccessDenied(module, method.toLowerCase(), module, null, msg);
        } catch (Exception e) {
            log.error("越权审计写入失败 uri={}", uri, e);
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(403, "无权限访问该资源")));
        return false;
    }

    private String deriveModule(String uri) {
        if (uri == null) {
            return "api";
        }
        String p = uri.replaceFirst("^/api/", "");
        int slash = p.indexOf('/');
        return slash > 0 ? p.substring(0, slash) : (p.isEmpty() ? "api" : p);
    }
}
