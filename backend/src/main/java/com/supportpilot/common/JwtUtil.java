package com.supportpilot.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：签发与解析无状态 token。
 *
 * <p>token 自包含 userId / username / tenantId / role，服务端无需存储会话，
 * 因此本项目鉴权不依赖 Redis。若后续接入 Redis，可在 AuthFilter 中增加黑名单校验实现主动登出。
 *
 * <p>解析失败（过期、签名错误、无 token）返回 null，由调用方决定是否放行。
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${app.security.jwt-secret}")
    private String secret;

    /** token 有效期（分钟），默认 30 分钟；配合 AuthFilter 的滑动续签实现「30 分钟无操作需重新登录」 */
    @Value("${app.security.token-expire-minutes:30}")
    private long expireMinutes;

    private SecretKey key;

    @PostConstruct
    void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** token 生命周期（毫秒），供续签判断使用 */
    public long lifetimeMillis() {
        return expireMinutes * 60_000L;
    }

    /**
     * 签发 token，写入用户主键信息（含显示名 / 工作组 / 数据范围，便于过滤器免查库构建上下文）。
     * teamId / dataScope 为 null 时 UserContext 会按角色兜底推断，兼容历史 token。
     */
    public String generateToken(Long userId, String username, String name, Long tenantId, String role,
                                Long teamId, String dataScope) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + lifetimeMillis());
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("username", username)
                .claim("name", name)
                .claim("tenantId", tenantId)
                .claim("role", role)
                .claim("teamId", teamId)
                .claim("dataScope", dataScope)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    /** 解析并验签，失败返回 null。 */
    public Claims parseClaims(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.debug("JWT 解析失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 滑动续签：token 签发超过生命周期 1/3 时（默认 10 分钟）换发新 token。
     * 有操作就续期，超过 30 分钟没有任何请求则 token 自然过期，需重新登录。
     * 未到续签时机或 claims 异常时返回 null。
     */
    public String maybeRefresh(Claims c) {
        Date issuedAt = c.getIssuedAt();
        if (issuedAt == null || System.currentTimeMillis() - issuedAt.getTime() < lifetimeMillis() / 3) {
            return null;
        }
        Long userId = toLong(c.get("userId"));
        if (userId == null) {
            return null;
        }
        return generateToken(userId,
                c.get("username", String.class),
                c.get("name", String.class),
                toLong(c.get("tenantId")),
                c.get("role", String.class),
                toLong(c.get("teamId")),
                c.get("dataScope", String.class));
    }

    public Long parseUserId(String token) {
        Claims c = parseClaims(token);
        return c == null ? null : toLong(c.get("userId"));
    }

    public String parseUsername(String token) {
        Claims c = parseClaims(token);
        return c == null ? null : c.get("username", String.class);
    }

    public String parseName(String token) {
        Claims c = parseClaims(token);
        return c == null ? null : c.get("name", String.class);
    }

    public String parseRole(String token) {
        Claims c = parseClaims(token);
        return c == null ? null : c.get("role", String.class);
    }

    public Long parseTenantId(String token) {
        Claims c = parseClaims(token);
        if (c == null) {
            return null;
        }
        Long tid = toLong(c.get("tenantId"));
        if (tid == null) {
            tid = toLong(c.get("tenant_id"));
        }
        return tid;
    }

    private Long toLong(Object v) {
        return v == null ? null : Long.valueOf(v.toString());
    }
}
