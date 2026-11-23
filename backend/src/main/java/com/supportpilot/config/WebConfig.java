package com.supportpilot.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局配置：CORS 放行 + RBAC 鉴权拦截器注册。
 *
 * <p>鉴权分层：AuthFilter（Filter）做认证 → 401；RoleInterceptor（Interceptor）做角色鉴权 → 403。
 * 登录 / 注册等无需登录的路径排除在拦截器之外。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RoleInterceptor roleInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**", "/error");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Refreshed-Token")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
