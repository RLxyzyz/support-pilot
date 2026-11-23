package com.supportpilot.rag;

/**
 * 检索增强（RAG）服务接口。
 * mock 实现返回预置上下文；real 实现为 S2 接入向量库的骨架。
 */
public interface RagService {

    /**
     * 根据工单检索相关文档 / 历史工单上下文。
     *
     * @return 检索结果（上下文 + 命中文档 id 列表 + 是否命中）
     */
    RagResult retrieve(String ticketId);
}
