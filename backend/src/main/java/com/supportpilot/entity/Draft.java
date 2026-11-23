package com.supportpilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "draft")
public class Draft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "run_id")
    private Long runId;

    @Column(columnDefinition = "text")
    private String content;

    private String cost;

    private String status;

    /** 生成时 RAG 命中文档 id 列表（逗号分隔），采纳后用于回填 kb_doc.hits。 */
    @Column(name = "cited_doc_ids")
    private String citedDocIds;

    /** 驳回原因（预设标签）：如 内容不准 / 语气不当 / 引用错误 / 其它。 */
    @Column(name = "feedback_reason")
    private String feedbackReason;

    /** 驳回备注（人工填写）。 */
    @Column(name = "feedback_comment")
    private String feedbackComment;

    /** 采纳时的最终编辑内容（与 AI 原始 content 不同时记录）。 */
    @Column(name = "edited_content")
    private String editedContent;

    /** M3 生成置信度 0-1。 */
    private Double confidence;

    /** M3 生成时的模型版本。 */
    @Column(name = "model_version", length = 64)
    private String modelVersion;

    /** M3 生成时的提示词版本。 */
    @Column(name = "prompt_version", length = 64)
    private String promptVersion;

    /** M3 生成时命中的知识库版本。 */
    @Column(name = "kb_version", length = 64)
    private String kbVersion;

    @Column(name = "created_at")
    private Instant createdAt;
}
