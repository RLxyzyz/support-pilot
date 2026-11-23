package com.supportpilot.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 轻量接口调用日志：每个请求打印一行 [METHOD] /uri?query -> 状态码 (耗时ms)。
 * 不读取请求体，避免破坏 Controller 的入参解析（body 读取会导致流不可重读）。
 * 5xx / 异常走 WARN/ERROR，正常请求走 INFO。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String method = request.getMethod();
        String path = request.getRequestURI();
        String query = request.getQueryString();
        String fullPath = (query == null || query.isEmpty()) ? path : path + "?" + query;

        try {
            chain.doFilter(request, response);
        } catch (Exception ex) {
            long cost = System.currentTimeMillis() - start;
            log.error("[{}] {} -> {} ({}ms) 异常: {}", method, fullPath, response.getStatus(), cost, ex.toString());
            throw ex;
        }

        long cost = System.currentTimeMillis() - start;
        int status = response.getStatus();
        if (status >= 500) {
            log.warn("[{}] {} -> {} ({}ms)", method, fullPath, status, cost);
        } else {
            log.info("[{}] {} -> {} ({}ms)", method, fullPath, status, cost);
        }
    }
}
