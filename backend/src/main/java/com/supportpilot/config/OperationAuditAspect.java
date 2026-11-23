package com.supportpilot.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.supportpilot.common.UserContext;
import com.supportpilot.common.audit.AuditContext;
import com.supportpilot.common.audit.AuditSnapshotProvider;
import com.supportpilot.common.audit.OperationAudit;
import com.supportpilot.service.OperationLogService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 操作审计切面：环绕 @OperationAudit 标注的 Controller 写方法，采集：
 * 操作人（仅从 UserContext，不信前端头）、IP/UA、操作前/后 DB 快照、
 * 请求参数（password 脱敏）、耗时、成功/失败与异常信息。
 *
 * <p>写库由 OperationLogService 异步完成；本切面任何异常都不影响业务方法执行。
 * 业务异常会原样 rethrow，仅在 finally 中记录 result=fail。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationAuditAspect {

    private final ObjectMapper objectMapper;
    private final List<AuditSnapshotProvider> snapshotProviders;
    private final OperationLogService operationLogService;

    private final SpelExpressionParser spelParser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer paramNameDiscoverer = new DefaultParameterNameDiscoverer();
    private Map<String, AuditSnapshotProvider> snapshotProviderMap;

    @PostConstruct
    void init() {
        snapshotProviderMap = new HashMap<>();
        for (AuditSnapshotProvider p : snapshotProviders) {
            snapshotProviderMap.put(p.targetType(), p);
        }
    }

    @Around("@annotation(operationAudit)")
    public Object around(ProceedingJoinPoint pjp, OperationAudit operationAudit) throws Throwable {
        long start = System.currentTimeMillis();
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        Object[] args = pjp.getArgs();

        String targetId = evalSpel(operationAudit.targetId(), method, args);
        String summary = renderSummary(operationAudit.summary(), targetId,
                operationAudit.action(), operationAudit.targetType());

        UserContext.CurrentUser u = UserContext.get();
        AuditContext ctx = new AuditContext(
                u == null ? null : u.getId(),
                u == null ? null : u.getName(),
                u == null ? null : u.getUsername(),
                u == null ? null : u.getTenantId(),
                operationAudit.module(),
                operationAudit.action(),
                operationAudit.targetType(),
                targetId,
                summary);

        // before 快照在方法执行前取（此时是旧状态）
        String beforeJson = snapshotJson(operationAudit.targetType(), targetId);
        String paramsJson = serializeArgsMasked(args);

        boolean success = true;
        String errorMsg = null;
        try {
            return pjp.proceed();
        } catch (Throwable e) {
            success = false;
            errorMsg = e.getMessage();
            throw e;
        } finally {
            long cost = System.currentTimeMillis() - start;
            // after 快照仅在成功时取（失败时数据未变更）
            String afterJson = success ? snapshotJson(operationAudit.targetType(), targetId) : null;
            try {
                operationLogService.record(ctx, paramsJson, beforeJson, afterJson, success, errorMsg, cost);
            } catch (Exception ex) {
                log.error("审计日志记录异常（已忽略，不影响业务）module={} action={}",
                        operationAudit.module(), operationAudit.action(), ex);
            }
        }
    }

    /** 用 SpEL 按参数名取值，如 "#id"；依赖 -parameters 编译选项（已开启）。 */
    private String evalSpel(String expr, Method method, Object[] args) {
        if (expr == null || expr.isBlank()) {
            return "";
        }
        try {
            StandardEvaluationContext ctx = new StandardEvaluationContext();
            String[] names = paramNameDiscoverer.getParameterNames(method);
            if (names != null) {
                for (int i = 0; i < names.length && i < args.length; i++) {
                    ctx.setVariable(names[i], args[i]);
                }
            }
            Object val = spelParser.parseExpression(expr).getValue(ctx);
            return val == null ? "" : val.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String renderSummary(String template, String targetId, String action, String targetType) {
        String s;
        if (template == null || template.isBlank()) {
            s = action + (blank(targetType) ? "" : " " + targetType)
                    + (blank(targetId) ? "" : " #" + targetId);
        } else {
            s = template;
        }
        return s.replace("{id}", targetId == null ? "" : targetId);
    }

    private String snapshotJson(String targetType, String targetId) {
        if (blank(targetType) || blank(targetId)) {
            return null;
        }
        AuditSnapshotProvider p = snapshotProviderMap.get(targetType);
        if (p == null) {
            return null;
        }
        try {
            Object data = p.load(targetId);
            if (data == null) {
                return null;
            }
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("快照序列化失败 type={} id={}: {}", targetType, targetId, e.getMessage());
            return null;
        }
    }

    /** 序列化方法参数为 JSON，递归把 key 含 password 的字段打码。 */
    private String serializeArgsMasked(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        try {
            List<Object> masked = new ArrayList<>();
            for (Object a : args) {
                masked.add(maskValue(a));
            }
            return objectMapper.writeValueAsString(masked);
        } catch (Exception e) {
            return null;
        }
    }

    private Object maskValue(Object o) {
        if (o == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.valueToTree(o);
            maskNode(node);
            return node;
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    private void maskNode(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            List<String> names = new ArrayList<>();
            obj.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                JsonNode child = obj.get(name);
                if (name.toLowerCase().contains("password") && child != null && !child.isNull()) {
                    obj.put(name, "***");
                } else {
                    maskNode(child);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode c : node) {
                maskNode(c);
            }
        }
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
