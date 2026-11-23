package com.supportpilot.config;

import com.supportpilot.common.JwtUtil;
import com.supportpilot.common.TenantContext;
import com.supportpilot.common.UserContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 全局鉴权过滤器：拦截 /api/**，校验 JWT 后把当前用户写入 UserContext / TenantContext。
 *
 * <p>放行规则：
 * <ul>
 *   <li>OPTIONS 预检直接放行（CORS）；</li>
 *   <li>app.security.permit-all 配置的白名单（默认 /api/auth/login、/actuator/**）放行；</li>
 *   <li>其余接口必须携带有效 token，否则返回 401。</li>
 * </ul>
 *
 * <p>token 取值顺序：先看 Authorization: Bearer 头，再看 query 参数 token。
 * 后者是为 SSE 准备的——浏览器 EventSource 不支持自定义请求头，前端只能把 token 拼在 URL 上。
 *
 * <p>预留 Redis 扩展点：若接入 Redis，可在校验通过后查 token 黑名单（主动登出/踢人），
 * 命中则返回 401；其余逻辑不变。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Value("${app.security.permit-all:/api/auth/login,/actuator/**}")
    private String permitAll;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String uri = request.getRequestURI();

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        for (String pattern : permitAll.split(",")) {
            if (pathMatcher.match(pattern.trim(), uri)) {
                chain.doFilter(request, response);
                return;
            }
        }

        String token = resolveToken(request);
        Claims claims = token == null ? null : jwtUtil.parseClaims(token);
        if (claims == null) {
            writeUnauthorized(response);
            return;
        }

        UserContext.CurrentUser user = new UserContext.CurrentUser(
                toLong(claims.get("userId")),
                claims.get("username", String.class),
                claims.get("name", String.class),
                toLong(claims.get("tenantId")),
                claims.get("role", String.class),
                toLong(claims.get("teamId")),
                claims.get("dataScope", String.class));
        UserContext.set(user);
        TenantContext.set(user.getTenantId());
        // 滑动续签：有操作就换发新 token（前端从响应头取回覆盖），超过 30 分钟无请求则自然过期
        String refreshed = jwtUtil.maybeRefresh(claims);
        if (refreshed != null) {
            response.setHeader("X-Refreshed-Token", refreshed);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        // EventSource 无法设置 header，SSE 请求通过 query 参数携带 token
        String q = request.getParameter("token");
        return (q == null || q.isBlank()) ? null : q;
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"未登录或登录已过期\",\"data\":null}");
    }

    private Long toLong(Object v) {
        return v == null ? null : Long.valueOf(v.toString());
    }
}
