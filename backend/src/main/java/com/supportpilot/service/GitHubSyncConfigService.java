package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.entity.GitHubSyncConfig;
import com.supportpilot.repository.GitHubSyncConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * GitHub Issue 同步配置：页面可改仓库、指定 Issue、同步条数与评论条数（0=全部）。
 *
 * <p>数据库中未保存配置时回落到 application.yml / 环境变量里的默认值，
 * 保证老环境不改配置也能继续按原方式同步。
 */
@Service
@RequiredArgsConstructor
public class GitHubSyncConfigService {

    private static final Pattern REPO_PART = Pattern.compile("^[A-Za-z0-9_.-]{1,100}$");
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    private final GitHubSyncConfigRepository configRepository;

    @Value("${app.github.enabled:true}")
    private boolean defaultEnabled;
    @Value("${app.github.owner:spring-projects}")
    private String defaultOwner;
    @Value("${app.github.repo:spring-boot}")
    private String defaultRepo;
    @Value("${app.github.token:}")
    private String defaultToken;
    @Value("${app.github.max-issues:20}")
    private int defaultMaxIssues;
    @Value("${app.github.max-comments:20}")
    private int defaultMaxComments;

    /** 生效配置（DB 优先，缺失回落默认值）。 */
    public SyncCfg effective() {
        GitHubSyncConfig c = configRepository.findFirstByOrderByIdAsc().orElse(null);
        if (c == null) {
            return new SyncCfg(defaultEnabled, defaultOwner, defaultRepo, defaultToken,
                    defaultMaxIssues, defaultMaxComments, null);
        }
        return new SyncCfg(
                c.getEnabled() == null ? defaultEnabled : c.getEnabled(),
                isBlank(c.getOwner()) ? defaultOwner : c.getOwner().trim(),
                isBlank(c.getRepo()) ? defaultRepo : c.getRepo().trim(),
                isBlank(c.getToken()) ? defaultToken : c.getToken().trim(),
                c.getMaxIssues() == null || c.getMaxIssues() <= 0 ? defaultMaxIssues : c.getMaxIssues(),
                c.getMaxComments() == null || c.getMaxComments() < 0 ? defaultMaxComments : c.getMaxComments(),
                c.getIssueNumber());
    }

    /** 页面展示用配置（Token 打码）。 */
    @Transactional(readOnly = true)
    public Map<String, Object> get() {
        SyncCfg cfg = effective();
        GitHubSyncConfig c = configRepository.findFirstByOrderByIdAsc().orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", cfg.enabled());
        m.put("owner", cfg.owner());
        m.put("repo", cfg.repo());
        m.put("issueNumber", cfg.issueNumber());
        m.put("maxIssues", cfg.maxIssues());
        m.put("maxComments", cfg.maxComments());
        m.put("hasToken", !isBlank(cfg.token()));
        m.put("tokenHint", maskToken(cfg.token()));
        m.put("lastSyncAt", c == null || c.getLastSyncAt() == null ? null : TIME_FMT.format(c.getLastSyncAt()));
        m.put("lastSyncResult", c == null ? null : c.getLastSyncResult());
        return m;
    }

    /** 保存配置：Token 留空表示保持原值。 */
    @Transactional
    public Map<String, Object> update(Map<String, Object> req) {
        String owner = str(req.get("owner"));
        String repo = str(req.get("repo"));
        if (!REPO_PART.matcher(owner).matches()) {
            throw BusinessException.badRequest("仓库所有者格式不正确（仅允许字母、数字、点、下划线、中划线）");
        }
        if (!REPO_PART.matcher(repo).matches()) {
            throw BusinessException.badRequest("仓库名称格式不正确（仅允许字母、数字、点、下划线、中划线）");
        }
        Integer issueNumber = intOrNull(req.get("issueNumber"));
        if (issueNumber != null && issueNumber <= 0) {
            throw BusinessException.badRequest("Issue 编号必须为正整数");
        }
        Integer maxIssues = intOrNull(req.get("maxIssues"));
        if (maxIssues == null || maxIssues < 1 || maxIssues > 100) {
            throw BusinessException.badRequest("单次同步条数需在 1 - 100 之间");
        }
        Integer maxComments = intOrNull(req.get("maxComments"));
        if (maxComments == null || maxComments < 0 || maxComments > 1000) {
            throw BusinessException.badRequest("评论条数需在 0 - 1000 之间（0 表示全部）");
        }

        GitHubSyncConfig c = configRepository.findFirstByOrderByIdAsc().orElseGet(GitHubSyncConfig::new);
        c.setEnabled(req.get("enabled") == null || Boolean.TRUE.equals(req.get("enabled")));
        c.setOwner(owner);
        c.setRepo(repo);
        c.setIssueNumber(issueNumber);
        c.setMaxIssues(maxIssues);
        c.setMaxComments(maxComments);
        String token = str(req.get("token"));
        if (!token.isBlank()) {
            c.setToken(token);
        }
        c.setUpdatedAt(Instant.now());
        configRepository.save(c);
        return get();
    }

    /** 记录最近一次同步时间与结果。 */
    @Transactional
    public void recordSync(String summary) {
        GitHubSyncConfig c = configRepository.findFirstByOrderByIdAsc().orElseGet(GitHubSyncConfig::new);
        c.setLastSyncAt(Instant.now());
        c.setLastSyncResult(summary);
        c.setUpdatedAt(Instant.now());
        configRepository.save(c);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static Integer intOrNull(Object o) {
        if (o == null || String.valueOf(o).isBlank()) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(String.valueOf(o)));
        } catch (NumberFormatException e) {
            throw BusinessException.badRequest("数字格式不正确：" + o);
        }
    }

    private static String maskToken(String token) {
        if (isBlank(token)) {
            return null;
        }
        if (token.length() <= 8) {
            return "****";
        }
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }

    /** 同步时使用的生效配置。 */
    public record SyncCfg(boolean enabled, String owner, String repo, String token,
                          int maxIssues, int maxComments, Integer issueNumber) {
    }
}
