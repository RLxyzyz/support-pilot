package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.common.TenantContext;
import com.supportpilot.common.UserContext;
import com.supportpilot.common.audit.AuditContext;
import com.supportpilot.dto.OperationLogPageVO;
import com.supportpilot.dto.OperationLogVO;
import com.supportpilot.entity.OperationLog;
import com.supportpilot.entity.User;
import com.supportpilot.repository.OperationLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 操作审计日志服务：
 * - record(...)：构造实体后异步落库，失败仅 log.error，绝不阻断业务、绝不静默。
 * - page / get：仅 admin 可查询（requireAdmin），按租户隔离。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogRepository operationLogRepository;
    private final ObjectMapper objectMapper;

    // ---------------- 写入 ----------------

    public void record(AuditContext ctx, String paramsJson, String beforeJson, String afterJson,
                       boolean success, String errorMsg, long costMs) {
        try {
            OperationLog entity = new OperationLog();
            entity.setTenantId(ctx.tenantId());
            entity.setOperatorId(ctx.operatorId());
            entity.setOperatorName(ctx.operatorName());
            entity.setOperatorUsername(ctx.operatorUsername());
            entity.setModule(ctx.module());
            entity.setAction(ctx.action());
            entity.setTargetType(blank(ctx.targetType()) ? null : ctx.targetType());
            entity.setTargetId(blank(ctx.targetId()) ? null : ctx.targetId());
            entity.setSummary(truncate(ctx.summary(), 512));
            entity.setBeforeData(beforeJson);
            entity.setAfterData(afterJson);
            entity.setRequestParams(paramsJson);
            entity.setResult(success ? "success" : "fail");
            entity.setErrorMsg(truncate(errorMsg, 2000));
            entity.setCostMs((int) Math.min(costMs, Integer.MAX_VALUE));
            entity.setCreatedAt(Instant.now());

            String[] ipUa = currentIpAndUa();
            entity.setIp(ipUa[0]);
            entity.setUserAgent(truncate(ipUa[1], 256));

            // 异步落库：在虚拟线程中 save，避免拖慢主接口；失败可观测但不影响业务
            Thread.startVirtualThread(() -> {
                try {
                    operationLogRepository.save(entity);
                } catch (Exception e) {
                    log.error("操作审计日志落库失败 module={} action={} target={}",
                            ctx.module(), ctx.action(), ctx.targetType(), e);
                }
            });
        } catch (Exception e) {
            log.error("操作审计日志构建失败 module={} action={}", ctx.module(), ctx.action(), e);
        }
    }

    /** 登录成功（UserContext 尚未建立，显式传入操作人） */
    public void recordLoginSuccess(User user) {
        AuditContext ctx = new AuditContext(user.getId(), user.getName(), user.getUsername(), user.getTenantId(),
                "auth", "login", "", "", "登录成功");
        record(ctx, null, null, null, true, null, 0);
    }

    /** 登录失败（用户名/密码错误）：记录尝试用户名，便于安全审计 */
    public void recordLoginFailure(String attemptedUsername, String errorMsg) {
        AuditContext ctx = new AuditContext(null, null, attemptedUsername, null,
                "auth", "login", "", "", "登录失败");
        record(ctx, null, null, null, false, errorMsg, 0);
    }

    /** 注册成功 */
    public void recordRegister(User user) {
        AuditContext ctx = new AuditContext(user.getId(), user.getName(), user.getUsername(), user.getTenantId(),
                "auth", "register", "user", String.valueOf(user.getId()), "注册成功");
        record(ctx, null, null, null, true, null, 0);
    }

    /** 注册失败（如用户名重复） */
    public void recordRegisterFailure(String attemptedUsername, String errorMsg) {
        AuditContext ctx = new AuditContext(null, null, attemptedUsername, null,
                "auth", "register", "", "", "注册失败");
        record(ctx, null, null, null, false, errorMsg, 0);
    }

    /** 越权访问（403）审计：从已建立的 UserContext 取操作人，写库失败仅 log 不抛。 */
    public void recordAccessDenied(String module, String action, String targetType, String targetId, String summary) {
        UserContext.CurrentUser u = UserContext.get();
        AuditContext ctx = new AuditContext(
                u == null ? null : u.getId(),
                u == null ? null : u.getName(),
                u == null ? null : u.getUsername(),
                TenantContext.get(),
                module, action, targetType, targetId, summary);
        record(ctx, null, null, null, false, "无权限访问", 0);
    }

    // ---------------- 查询（仅 admin） ----------------

    public OperationLogPageVO page(String module, String action, String operator,
                                   Instant start, Instant end, int page, int size) {
        requireAdmin();
        Long tid = TenantContext.get();

        Specification<OperationLog> spec = (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (tid != null) {
                preds.add(cb.equal(root.get("tenantId"), tid));
            }
            if (!blank(module)) {
                preds.add(cb.equal(root.get("module"), module));
            }
            if (!blank(action)) {
                preds.add(cb.equal(root.get("action"), action));
            }
            if (!blank(operator)) {
                preds.add(cb.like(cb.lower(root.get("operatorName")), "%" + operator.toLowerCase() + "%"));
            }
            if (start != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start));
            }
            if (end != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("createdAt"), end));
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };

        int pageIndex = Math.max(page - 1, 0);
        int pageSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<OperationLog> result = operationLogRepository.findAll(spec, pageable);

        List<OperationLogVO> list = result.getContent().stream()
                .map(e -> toVO(e, false))
                .toList();
        return new OperationLogPageVO(list, result.getTotalElements());
    }

    public OperationLogVO get(Long id) {
        requireAdmin();
        OperationLog log = operationLogRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "日志不存在"));
        // 租户隔离：非该租户 admin 不可见
        Long tid = TenantContext.get();
        if (tid != null && log.getTenantId() != null && !tid.equals(log.getTenantId())) {
            throw new BusinessException(404, "日志不存在");
        }
        return toVO(log, true);
    }

    // ---------------- 内部工具 ----------------

    private void requireAdmin() {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(403, "需要管理员权限");
        }
    }

    private OperationLogVO toVO(OperationLog e, boolean withDetail) {
        OperationLogVO vo = new OperationLogVO();
        vo.setId(e.getId());
        vo.setTenantId(e.getTenantId());
        vo.setOperatorId(e.getOperatorId());
        vo.setOperatorName(e.getOperatorName());
        vo.setOperatorUsername(e.getOperatorUsername());
        vo.setModule(e.getModule());
        vo.setAction(e.getAction());
        vo.setTargetType(e.getTargetType());
        vo.setTargetId(e.getTargetId());
        vo.setSummary(e.getSummary());
        vo.setResult(e.getResult());
        vo.setErrorMsg(e.getErrorMsg());
        vo.setIp(e.getIp());
        vo.setUserAgent(e.getUserAgent());
        vo.setCostMs(e.getCostMs());
        vo.setCreatedAt(e.getCreatedAt());
        if (withDetail) {
            vo.setBeforeData(e.getBeforeData());
            vo.setAfterData(e.getAfterData());
            vo.setRequestParams(e.getRequestParams());
        }
        return vo;
    }

    private String[] currentIpAndUa() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
                HttpServletRequest req = sra.getRequest();
                return new String[]{clientIp(req), req.getHeader("User-Agent")};
            }
        } catch (Exception ignored) {
            // 请求上下文不可用时（如非 HTTP 触发）返回空，不阻断
        }
        return new String[]{null, null};
    }

    private String clientIp(HttpServletRequest req) {
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String h : headers) {
            String v = req.getHeader(h);
            if (v != null && !v.isBlank() && !"unknown".equalsIgnoreCase(v)) {
                return v.split(",")[0].trim();
            }
        }
        return req.getRemoteAddr();
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
