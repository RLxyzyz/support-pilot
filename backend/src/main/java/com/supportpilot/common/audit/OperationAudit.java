package com.supportpilot.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 Controller 写操作方法上，由 OperationAuditAspect 环绕采集审计日志。
 *
 * <p>targetId 用 SpEL 从方法参数取值（如 #id），需编译开启 -parameters（已在 pom 配置）。
 * targetId 为空时不采集 before/after 快照（如 create 类操作）。
 * summary 支持 {id} 占位符，会被替换为解析后的 targetId；为空则用 "action targetType #id" 兜底。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationAudit {

    String module();

    String action();

    String targetType() default "";

    String targetId() default "";

    String summary() default "";
}
