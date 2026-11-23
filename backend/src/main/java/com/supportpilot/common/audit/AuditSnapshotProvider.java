package com.supportpilot.common.audit;

/**
 * 操作前/后快照提供者：按 targetType 从 DB 加载当前持久化状态。
 * 返回可被 Jackson 序列化的对象（通常为 Map），避免直接序列化带懒加载关联的实体。
 * 切面在方法执行前调用取 before、执行成功后调用取 after。
 */
public interface AuditSnapshotProvider {

    String targetType();

    Object load(String targetId);
}
