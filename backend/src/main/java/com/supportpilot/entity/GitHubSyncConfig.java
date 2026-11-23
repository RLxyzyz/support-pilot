package com.supportpilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

/**
 * GitHub Issue 同步配置（全局单行）。
 *
 * <p>页面上可配置目标仓库、指定 Issue 编号（为空则按最近更新拉取多条）、
 * 单次同步条数、每条 Issue 的评论条数（0 表示全部）以及访问 Token。
 */
@Data
@Entity
@Table(name = "github_sync_config")
public class GitHubSyncConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Boolean enabled;

    private String owner;

    private String repo;

    /** 指定单个 Issue 编号；为空表示按最近更新时间同步多条。 */
    @Column(name = "issue_number")
    private Integer issueNumber;

    @Column(name = "max_issues")
    private Integer maxIssues;

    /** 每条 Issue 拉取的评论数；0 表示全部评论。 */
    @Column(name = "max_comments")
    private Integer maxComments;

    private String token;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "last_sync_result")
    private String lastSyncResult;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
