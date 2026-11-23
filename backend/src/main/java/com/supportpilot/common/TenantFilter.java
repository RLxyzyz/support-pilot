package com.supportpilot.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 租户上下文过滤器：解析 Authorization 头中的 JWT，提取 tenantId 放入 TenantContext。
 * 无 token 或解析失败时 tenantId 为 null（演示模式，不强制过滤）。
 */
@Component
// 让 AuthFilter（最高优先级）先完成鉴权，再由本过滤器补充租户上下文
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Long tenantId = null;
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            tenantId = jwtUtil.parseTenantId(auth.substring(7));
        }
        TenantContext.set(tenantId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
