package com.supportpilot.rag;

import java.util.List;

/**
 * RAG 检索结果：拼入 prompt 的上下文 + 命中文档 id 列表（用于采纳后回填 kb_doc.hits）。
 *
 * @param context    可拼入 prompt 的上下文字符串，无命中时为空串
 * @param hitDocIds  命中的 kb_doc.id 列表（去重），用于采纳后命中统计；无命中为空
 * @param hit        是否命中（hitDocIds 非空）
 */
public record RagResult(String context, List<Long> hitDocIds, boolean hit) {

    public static final RagResult EMPTY = new RagResult("", List.of(), false);
}
