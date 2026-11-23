package com.supportpilot.rag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Mock 检索：仅在 app.ai.mock-enabled=true 时启用。
 *
 * <p>不再注入固定的 Spring Boot 文档片段，避免所有工单都被引导到同一领域；
 * 返回空结果，由 prompt 走「未检索到知识库依据」分支，按工单内容本身作答。
 */
@Service
@ConditionalOnProperty(name = "app.ai.mock-enabled", havingValue = "true", matchIfMissing = true)
public class MockRagService implements RagService {

    @Override
    public RagResult retrieve(String ticketId) {
        return RagResult.EMPTY;
    }
}
