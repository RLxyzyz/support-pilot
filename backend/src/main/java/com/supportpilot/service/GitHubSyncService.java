package com.supportpilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportpilot.common.DisplayUtil;
import com.supportpilot.common.NotifyType;
import com.supportpilot.common.TimeFormat;
import com.supportpilot.entity.Contact;
import com.supportpilot.entity.Customer;
import com.supportpilot.entity.Product;
import com.supportpilot.entity.Tenant;
import com.supportpilot.entity.Ticket;
import com.supportpilot.entity.TicketMessage;
import com.supportpilot.repository.ContactRepository;
import com.supportpilot.repository.CustomerRepository;
import com.supportpilot.repository.ProductRepository;
import com.supportpilot.repository.TenantRepository;
import com.supportpilot.repository.TicketMessageRepository;
import com.supportpilot.repository.TicketRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GitHub Issue 定时同步服务。
 *
 * <p>拉取指定仓库（默认 spring-projects/spring-boot）的 issue（含 open 与 closed），过滤掉 PR，
 * 按 {@code source + source_ref} 幂等去重后写入 ticket 表，并把 issue 评论写入 ticket_message，
 * 作为工单系统的真实外部数据来源。
 *
 * <p>同步策略：
 * <ul>
 *   <li><b>新工单</b>：首次入库，全量拉评论；租户/产品按 label 映射（未命中用默认）；</li>
 *   <li><b>已存在工单</b>：比较 {@code github_updated_at}，有变化才增量更新——同步新评论（按 comment id 去重）、
 *       单向推进状态（GitHub 已关闭则置已解决，但<b>不回退已解决</b>，避免覆盖人工采纳的结果）；
 *       不覆盖标题/正文/优先级/租户/产品，尊重人工修改；</li>
 *   <li><b>无变化</b>：跳过。</li>
 * </ul>
 *
 * <p>触发方式：{@link ApplicationReadyEvent} 启动后虚拟线程立即同步一次；{@link Scheduled} 按 cron 定时同步。
 * 设 {@code app.github.enabled=false} 可整体关闭。token 留空走匿名 API（60 次/小时），配置后 5000 次/小时。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubSyncService {

    private static final String SOURCE = "github";
    private static final String API_BASE = "https://api.github.com";

    private final TicketRepository ticketRepository;
    private final TicketMessageRepository ticketMessageRepository;
    private final TenantRepository tenantRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final ContactRepository contactRepository;
    private final SlaService slaService;
    private final GitHubSyncConfigService configService;
    private final TicketAssignmentService ticketAssignmentService;
    private final NotificationDispatcher notificationDispatcher;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${app.github.default-tenant-id:1}")
    private Long defaultTenantId;
    @Value("${app.github.default-product-id:1}")
    private Long defaultProductId;

    /** label 映射：label 名 → 租户 id（如 for:external=2）。空表示全部用默认租户。 */
    @Value("${app.github.label-tenant-map:}")
    private String labelTenantMapRaw;
    /** label 映射：label 名 → 产品 id（如 type:bug=1）。空表示全部用默认产品。 */
    @Value("${app.github.label-product-map:}")
    private String labelProductMapRaw;
    /** label 映射：label 名 → 优先级（如 priority:critical=P1）。优先于关键词推断。 */
    @Value("${app.github.label-priority-map:}")
    private String labelPriorityMapRaw;

    private Map<String, Long> labelTenantMap;
    private Map<String, Long> labelProductMap;
    private Map<String, String> labelPriorityMap;

    @PostConstruct
    void initLabelMaps() {
        this.labelTenantMap = parseLongMap(labelTenantMapRaw);
        this.labelProductMap = parseLongMap(labelProductMapRaw);
        this.labelPriorityMap = parseStringMap(labelPriorityMapRaw);
    }

    /** 启动就绪后立即同步一次（异步，不阻塞启动；失败不影响应用）。 */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!configService.effective().enabled()) {
            log.info("GitHub 同步已关闭（页面配置或 app.github.enabled=false），跳过启动同步");
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(2000); // 错开启动日志
                syncIssues();
            } catch (Exception e) {
                log.warn("启动时 GitHub 同步失败（不影响应用）: {}", e.getMessage());
            }
        });
    }

    /** 定时同步，cron 由 app.github.sync-cron 配置（默认每小时整点）。 */
    @Scheduled(cron = "${app.github.sync-cron:0 0 * * * ?}")
    public void scheduledSync() {
        if (!configService.effective().enabled()) {
            return;
        }
        try {
            syncIssues();
        } catch (Exception e) {
            log.warn("定时 GitHub 同步失败: {}", e.getMessage());
        }
    }

    /**
     * 同步主流程：拉 issue（含 closed）→ 新工单入库 / 已存在工单增量更新。
     *
     * <p>仓库、指定 Issue、同步条数、评论条数都取自页面上保存的配置；
     * 评论条数为 0 表示拉取全部评论。
     */
    @Transactional
    public Map<String, Object> syncIssues() {
        GitHubSyncConfigService.SyncCfg cfg = configService.effective();
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("owner", cfg.owner());
        result.put("repo", cfg.repo());
        result.put("issueNumber", cfg.issueNumber());
        result.put("maxIssues", cfg.maxIssues());
        result.put("maxComments", cfg.maxComments());

        if (!cfg.enabled()) {
            result.put("message", "同步已关闭");
            result.put("issues", 0);
            result.put("created", 0);
            result.put("updated", 0);
            result.put("skipped", 0);
            return result;
        }

        log.info("开始同步 GitHub Issue: {}/{}（指定 Issue={}，候选上限={}，评论上限={}）",
                cfg.owner(), cfg.repo(), cfg.issueNumber() == null ? "全部" : cfg.issueNumber(),
                cfg.maxIssues(), cfg.maxComments() == 0 ? "全部" : cfg.maxComments());

        List<JsonNode> issues = cfg.issueNumber() != null ? fetchOneIssue(cfg) : fetchIssues(cfg);
        if (issues.isEmpty()) {
            log.info("未拉取到 issue，结束同步");
            String msg = cfg.issueNumber() != null
                    ? "未拉取到 Issue #" + cfg.issueNumber() + "（不存在或无权限）"
                    : "未拉取到 issue";
            configService.recordSync(msg);
            result.put("message", msg);
            result.put("issues", 0);
            result.put("created", 0);
            result.put("updated", 0);
            result.put("skipped", 0);
            return result;
        }

        // 预加载全部租户/产品到内存，供 label 映射查找，避免循环内反复查库
        Map<Long, Tenant> tenantMap = new HashMap<>();
        tenantRepository.findAll().forEach(t -> tenantMap.put(t.getId(), t));
        Map<Long, Product> productMap = new HashMap<>();
        productRepository.findAll().forEach(p -> productMap.put(p.getId(), p));
        Tenant defaultTenant = tenantMap.get(defaultTenantId);
        Product defaultProduct = productMap.get(defaultProductId);

        long nextId = ticketRepository.findTopByOrderByIdDesc()
                .map(t -> t.getId() + 1).orElse(41000L);

        // 仓库级产品：用于历史工单产品归属修正（此前无 label 映射时全部落在默认产品）
        Product repoProduct = resolveRepoProduct(productMap, defaultProduct, defaultTenant, cfg.repo());
        int productFixed = 0;
        if (repoProduct != null && !repoProduct.getId().equals(defaultProductId)) {
            productFixed = ticketRepository.fixGithubTicketProduct(
                    cfg.owner() + "/" + cfg.repo() + "#", repoProduct.getId());
            if (productFixed > 0) {
                log.info("已修正 {} 条历史 GitHub 工单的产品为 {}", productFixed, repoProduct.getName());
            }
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (JsonNode issue : issues) {
            if (issue.has("pull_request")) {
                skipped++;
                continue; // GitHub /issues 接口会混入 PR，跳过
            }
            long number = issue.path("number").asLong();
            String sourceRef = cfg.owner() + "/" + cfg.repo() + "#" + number;
            var existing = ticketRepository.findBySourceAndSourceRef(SOURCE, sourceRef);
            Tenant tenant = resolveTenant(issue, tenantMap, defaultTenant);
            Product product = resolveProduct(issue, productMap, defaultProduct, tenant, cfg.repo());
            if (existing.isPresent()) {
                Ticket t = existing.get();
                // 历史未归户工单补归户：按 Issue 作者匹配/创建客户
                if (t.getCustomerId() == null) {
                    attributeCustomer(t, issue, tenant);
                }
                // 已存在工单也按本次同步时刻刷新创建/更新时间
                t.setCreatedAt(Instant.now());
                t.setUpdateTime(TimeFormat.format(Instant.now()));
                if (updateIfChanged(t, issue, cfg, product)) {
                    updated++;
                } else {
                    ticketRepository.save(t);
                    skipped++;
                }
                continue;
            }
            Ticket ticket = buildTicket(issue, sourceRef, nextId++, tenant, product);
            // 归户：按 Issue 作者匹配联系人，未命中自动创建「客户 + 主联系人」
            attributeCustomer(ticket, issue, tenant);
            // 自动派单：未解决的新同步工单按支持人员当前持单量均分；已关闭的直接归档不分派
            if (!"已解决".equals(ticket.getStatus())) {
                ticket.setAssigneeId(ticketAssignmentService.pickLeastLoaded(
                        tenant == null ? null : tenant.getId()));
            }
            ticketRepository.save(ticket);
            if (ticket.getAssigneeId() != null) {
                // 自动派单同样发「分派」通知：站内信 + 已开启的邮件通道
                notificationDispatcher.dispatchTicketEvent(
                        NotifyType.TICKET_ASSIGNED, ticket, ticket.getAssigneeId(), "GitHub 同步自动派单");
            }
            for (TicketMessage msg : fetchComments(cfg, number, ticket)) {
                ticketMessageRepository.save(msg);
            }
            created++;
        }
        String msg = String.format("候选 %d 条，新增 %d 条，更新 %d 条，跳过 %d 条", issues.size(), created, updated, skipped)
                + (productFixed > 0 ? "，修正产品 " + productFixed + " 条" : "");
        log.info("GitHub 同步完成：{}", msg);
        configService.recordSync(msg);

        result.put("message", msg);
        result.put("issues", issues.size());
        result.put("created", created);
        result.put("updated", updated);
        result.put("skipped", skipped);
        return result;
    }

    /**
     * 已存在工单的增量更新：比较 github_updated_at，有变化才更新。
     * 仅同步状态（单向）与新评论，不覆盖标题/正文/优先级/租户/产品（尊重人工修改）。
     *
     * @return true 表示有变化并执行了更新
     */
    private boolean updateIfChanged(Ticket existing, JsonNode issue, GitHubSyncConfigService.SyncCfg cfg,
                                    Product resolvedProduct) {
        boolean changed = false;
        // 0. 产品修正：历史数据未命中 label 映射时落在默认产品，按当前仓库名纠正
        Long currentProductId = existing.getProduct() == null ? null : existing.getProduct().getId();
        if (resolvedProduct != null
                && (currentProductId == null || currentProductId.equals(defaultProductId))
                && !resolvedProduct.getId().equals(currentProductId)) {
            existing.setProduct(resolvedProduct);
            changed = true;
        }
        String ghUpdated = issue.hasNonNull("updated_at") ? issue.path("updated_at").asText() : "";
        // 本地已记录且与 GitHub 一致 → 无变化，跳过（产品被纠正时仍落库）
        if (ghUpdated.equals(existing.getGithubUpdatedAt())) {
            if (changed) {
                ticketRepository.save(existing);
            }
            return changed;
        }
        String oldStatus = existing.getStatus();

        // 1. 状态单向同步：GitHub 已关闭且本地非已解决 → 置已解决（不回退已解决，避免覆盖人工采纳）
        boolean closed = "closed".equalsIgnoreCase(issue.path("state").asText("open"));
        if (closed && !"已解决".equals(existing.getStatus())) {
            existing.setStatus("已解决");
            existing.setStatusClass(DisplayUtil.statusClass("已解决"));
            existing.setSla("已完成");
            existing.setSlaPct(100);
            existing.setSlaColor("#cbd5e1");
            if (existing.getResolvedAt() == null) {
                existing.setResolvedAt(Instant.now());
            }
            changed = true;
        }

        // 2. 增量拉取新评论（按 external_id 去重）；客户回复自动重开（待客户 → 处理中）
        int newComments = syncCommentsIncremental(existing, issue.path("number").asLong(), cfg);
        if (newComments > 0) {
            changed = true;
        }

        // 3. SLA 暂停计时统一结算（以最终状态为准，含评论触发的重开）
        slaService.onStatusChange(existing, oldStatus, existing.getStatus());

        // 4. 记录本次同步时间戳 + 刷新相对时间
        existing.setGithubUpdatedAt(ghUpdated);
        // 更新时间按同步时刻记录，不使用 issue 的实际更新时间
        existing.setUpdateTime(TimeFormat.format(Instant.now()));
        ticketRepository.save(existing);
        return changed;
    }

    /** 增量同步某 issue 的新评论：已存在（按 comment id）的跳过，只插入新评论。返回新增条数。 */
    private int syncCommentsIncremental(Ticket ticket, long issueNumber, GitHubSyncConfigService.SyncCfg cfg) {
        int count = 0;
        for (TicketMessage msg : fetchComments(cfg, issueNumber, ticket)) {
            if (msg.getExternalId() != null
                    && ticketMessageRepository.existsByTicketIdAndExternalId(ticket.getId(), msg.getExternalId())) {
                continue;
            }
            ticketMessageRepository.save(msg);
            count++;
        }
        // 客户回复自动重开：GitHub 评论视为客户侧消息；处于「待客户」时自动回到「处理中」
        if (count > 0 && "待客户".equals(ticket.getStatus())) {
            ticket.setStatus("处理中");
            ticket.setStatusClass(DisplayUtil.statusClass("处理中"));
            ticket.setUpdateTime(TimeFormat.format(Instant.now()));
        }
        return count;
    }

    /** GitHub issue JSON → Ticket 实体。 */
    private Ticket buildTicket(JsonNode issue, String sourceRef, long id, Tenant tenant, Product product) {
        String state = issue.path("state").asText("open");
        boolean closed = "closed".equalsIgnoreCase(state);
        String status = closed ? "已解决" : "待分诊";
        String title = issue.path("title").asText("(无标题)");
        String body = issue.hasNonNull("body") ? issue.path("body").asText() : "";
        String htmlUrl = issue.hasNonNull("html_url") ? issue.path("html_url").asText() : "";
        String updatedAt = issue.hasNonNull("updated_at") ? issue.path("updated_at").asText() : "";

        JsonNode labels = issue.path("labels");

        // 分类：取第一个非空 label 名
        String category = "GitHub Issue";
        if (labels.isArray()) {
            for (JsonNode label : labels) {
                String name = label.path("name").asText("");
                if (!name.isBlank()) {
                    category = name;
                    break;
                }
            }
        }

        // 优先级：label 精确映射优先，未命中走关键词兜底
        String priority = matchPriority(issue);
        if (priority == null) {
            priority = inferPriorityByKeyword(labels);
        }

        Ticket t = new Ticket();
        t.setId(id);
        t.setSource(SOURCE);
        t.setSourceRef(sourceRef);
        t.setTenant(tenant);
        t.setProduct(product);
        t.setTitle(title);
        t.setBody(body + (htmlUrl.isBlank() ? "" : "\n\n来源：" + htmlUrl));
        t.setCode("");
        t.setPriority(priority);
        t.setPriorityClass(DisplayUtil.priorityClass(priority));
        t.setStatus(status);
        t.setStatusClass(DisplayUtil.statusClass(status));
        t.setCategory(category);
        t.setSla(closed ? "已完成" : "剩余 4 小时");
        t.setSlaPct(closed ? 100 : 85);
        t.setSlaColor(closed ? "#cbd5e1" : "#d97706");
        // 创建/更新时间统一取同步时刻，不使用 issue 的 created_at / updated_at
        t.setUpdateTime(TimeFormat.format(Instant.now()));
        t.setGithubUpdatedAt(updatedAt); // 记录本次同步的 issue 更新时间，用于下次增量判断
        t.setCreatedAt(Instant.now());
        if (closed) {
            // 已关闭的新工单直接写入解决时间（取 GitHub closed_at，解析失败兜底当前时间），保证 MTTR 数据完整
            String closedAt = issue.hasNonNull("closed_at") ? issue.path("closed_at").asText() : "";
            t.setResolvedAt(parseInstant(closedAt));
        }
        return t;
    }

    /**
     * 按 Issue 作者自动归户：优先复用同租户同名联系人；否则匹配同名客户；
     * 都没有则以作者登录名新建「客户 + 主联系人」，并挂到工单上。
     */
    private void attributeCustomer(Ticket ticket, JsonNode issue, Tenant tenant) {
        String login = issue.path("user").path("login").asText("");
        if (login.isBlank()) {
            return;
        }
        Long tid = tenant != null ? tenant.getId() : defaultTenantId;

        // 1. 同租户同名联系人直接复用
        var existingContact = contactRepository.findFirstByTenantIdAndNameIgnoreCase(tid, login);
        if (existingContact.isPresent()) {
            Contact contact = existingContact.get();
            ticket.setContactId(contact.getId());
            ticket.setCustomerId(contact.getCustomerId());
            return;
        }

        // 2. 同名客户：挂到该客户下并补一个主联系人
        Customer customer = customerRepository.findByNameIgnoreCase(login).stream()
                .filter(c -> c.getTenantId() == null || c.getTenantId().equals(tid))
                .findFirst().orElse(null);
        if (customer == null) {
            customer = new Customer();
            customer.setTenantId(tid);
            customer.setName(login);
            customer.setCode(uniqueCustomerCode(tid, login));
            customer.setSlaTier("默认");
            customer.setStatus("active");
            customer.setCreatedAt(Instant.now());
            customer = customerRepository.save(customer);
        }

        Contact contact = new Contact();
        contact.setTenantId(customer.getTenantId() != null ? customer.getTenantId() : tid);
        contact.setCustomerId(customer.getId());
        contact.setName(login);
        contact.setIsPrimary(true);
        contact.setCreatedAt(Instant.now());
        contact = contactRepository.save(contact);

        ticket.setCustomerId(customer.getId());
        ticket.setContactId(contact.getId());
    }

    /** 租户内唯一的客户编码：GH-&lt;登录名&gt;，超长截断，冲突加序号。 */
    private String uniqueCustomerCode(Long tid, String login) {
        String base = "GH-" + login;
        if (base.length() > 64) {
            base = base.substring(0, 64);
        }
        if (tid == null) {
            return base;
        }
        String code = base;
        int i = 2;
        while (customerRepository.existsByTenantIdAndCode(tid, code)) {
            String suffix = "-" + i++;
            code = base.substring(0, Math.min(base.length(), 64 - suffix.length())) + suffix;
        }
        return code;
    }

    /** label 名包含 critical → P1，包含 high → P2，否则 P3。 */
    private String inferPriorityByKeyword(JsonNode labels) {
        String priority = "P3";
        if (labels.isArray()) {
            for (JsonNode label : labels) {
                String lower = label.path("name").asText("").toLowerCase();
                if (lower.contains("critical")) {
                    return "P1";
                }
                if (lower.contains("high") && !"P1".equals(priority)) {
                    priority = "P2";
                }
            }
        }
        return priority;
    }

    /** 按 label 精确映射匹配优先级，未命中返回 null（交由关键词兜底）。 */
    private String matchPriority(JsonNode issue) {
        if (labelPriorityMap == null || labelPriorityMap.isEmpty()) {
            return null;
        }
        JsonNode labels = issue.path("labels");
        if (labels.isArray()) {
            for (JsonNode label : labels) {
                String name = label.path("name").asText("");
                if (labelPriorityMap.containsKey(name)) {
                    return labelPriorityMap.get(name);
                }
            }
        }
        return null;
    }

    /** 按 label 映射解析租户：命中返回对应租户，未命中返回默认租户。 */
    private Tenant resolveTenant(JsonNode issue, Map<Long, Tenant> tenantMap, Tenant def) {
        Long id = matchLabelId(issue, labelTenantMap);
        return id != null ? tenantMap.getOrDefault(id, def) : def;
    }

    /**
     * 解析产品：label 映射命中优先；否则按仓库名匹配/自动创建产品，
     * 保证同步进来的工单产品是「实际仓库」，而不是固定落到默认产品（Spring Boot）。
     */
    private Product resolveProduct(JsonNode issue, Map<Long, Product> productMap, Product def,
                                   Tenant tenant, String repoName) {
        Long id = matchLabelId(issue, labelProductMap);
        if (id != null) {
            Product mapped = productMap.get(id);
            if (mapped != null) {
                return mapped;
            }
        }
        return resolveRepoProduct(productMap, def, tenant, repoName);
    }

    /**
     * 按仓库名解析产品：先做归一化模糊匹配（如 repo=spring-boot 命中「Spring Boot 3.3.0」），
     * 未命中则在租户下自动创建以仓库名命名的产品。
     */
    private Product resolveRepoProduct(Map<Long, Product> productMap, Product def,
                                       Tenant tenant, String repoName) {
        if (repoName == null || repoName.isBlank()) {
            return def;
        }
        String norm = repoName.toLowerCase().replaceAll("[^a-z0-9]", "");
        Long tenantId = tenant == null ? defaultTenantId : tenant.getId();
        for (Product p : productMap.values()) {
            if (p.getTenantId() == null || !p.getTenantId().equals(tenantId)) {
                continue;
            }
            String pn = p.getName() == null ? "" : p.getName().toLowerCase().replaceAll("[^a-z0-9]", "");
            if (norm.equals(pn) || (!norm.isEmpty() && pn.contains(norm))) {
                return p;
            }
        }
        Product created = new Product();
        created.setId(productRepository.findTopByOrderByIdDesc().map(p -> p.getId() + 1).orElse(1L));
        created.setTenantId(tenantId);
        created.setName(repoName);
        created.setCreatedAt(Instant.now());
        productRepository.save(created);
        productMap.put(created.getId(), created);
        log.info("GitHub 同步自动创建产品：{}（租户 {}）", repoName, tenantId);
        return created;
    }

    /** 遍历 issue labels，返回首个命中映射表的 value（租户/产品 id），未命中返回 null。 */
    private Long matchLabelId(JsonNode issue, Map<String, Long> labelMap) {
        if (labelMap == null || labelMap.isEmpty()) {
            return null;
        }
        JsonNode labels = issue.path("labels");
        if (labels.isArray()) {
            for (JsonNode label : labels) {
                String name = label.path("name").asText("");
                if (labelMap.containsKey(name)) {
                    return labelMap.get(name);
                }
            }
        }
        return null;
    }

    /**
     * 拉取单条 issue 的评论，作为工单会话消息；external_id 存 GitHub comment id 用于去重。
     * 评论上限为 0 时按每页 100 条循环拉取全部评论。
     */
    private List<TicketMessage> fetchComments(GitHubSyncConfigService.SyncCfg cfg, long issueNumber, Ticket ticket) {
        List<TicketMessage> result = new ArrayList<>();
        int limit = cfg.maxComments();
        int page = 1;
        while (true) {
            int perPage = limit > 0 ? Math.min(limit, 100) : 100;
            String url = String.format("%s/repos/%s/%s/issues/%d/comments?per_page=%d&page=%d",
                    API_BASE, cfg.owner(), cfg.repo(), issueNumber, perPage, page);
            JsonNode arr;
            try {
                HttpResponse<String> resp = httpClient.send(newRequest(url, cfg.token()),
                        HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) {
                    log.debug("拉取评论失败 issue#{} status={}", issueNumber, resp.statusCode());
                    break;
                }
                arr = objectMapper.readTree(resp.body());
            } catch (Exception e) {
                log.debug("拉取评论异常 issue#{}: {}", issueNumber, e.getMessage());
                break;
            }
            if (!arr.isArray() || arr.isEmpty()) {
                break;
            }
            for (JsonNode c : arr) {
                if (limit > 0 && result.size() >= limit) {
                    return result;
                }
                TicketMessage m = new TicketMessage();
                m.setTicket(ticket);
                m.setWho(c.path("user").path("login").asText("匿名"));
                m.setRole("客户 · GitHub");
                m.setText(c.path("body").asText(""));
                m.setInternal(false);
                if (c.hasNonNull("id")) {
                    m.setExternalId(c.path("id").asLong());
                }
                result.add(m);
            }
            // 指定了上限，或本页不足一页（全部评论已取完），结束
            if (limit > 0 || arr.size() < 100 || page >= 20) {
                break;
            }
            page++;
        }
        return result;
    }

    /** 拉取 issue 列表（含 open 与 closed，按更新时间倒序），条数取自页面配置。 */
    private List<JsonNode> fetchIssues(GitHubSyncConfigService.SyncCfg cfg) {
        try {
            String url = String.format("%s/repos/%s/%s/issues?state=all&per_page=%d&sort=updated&direction=desc",
                    API_BASE, cfg.owner(), cfg.repo(), Math.min(cfg.maxIssues(), 100));
            HttpResponse<String> resp = httpClient.send(newRequest(url, cfg.token()),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                String body = resp.body();
                log.warn("拉取 issue 失败 status={} body={}", resp.statusCode(),
                        body.length() > 200 ? body.substring(0, 200) : body);
                return List.of();
            }
            JsonNode arr = objectMapper.readTree(resp.body());
            if (!arr.isArray()) {
                return List.of();
            }
            List<JsonNode> list = new ArrayList<>();
            arr.forEach(list::add);
            return list;
        } catch (Exception e) {
            log.warn("拉取 issue 异常: {}", e.getMessage());
            return List.of();
        }
    }

    /** 拉取指定编号的单条 issue；不存在或无权限返回空列表。 */
    private List<JsonNode> fetchOneIssue(GitHubSyncConfigService.SyncCfg cfg) {
        try {
            String url = String.format("%s/repos/%s/%s/issues/%d",
                    API_BASE, cfg.owner(), cfg.repo(), cfg.issueNumber());
            HttpResponse<String> resp = httpClient.send(newRequest(url, cfg.token()),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                String body = resp.body();
                log.warn("拉取指定 Issue #{} 失败 status={} body={}", cfg.issueNumber(), resp.statusCode(),
                        body.length() > 200 ? body.substring(0, 200) : body);
                return List.of();
            }
            JsonNode node = objectMapper.readTree(resp.body());
            return node.isObject() ? List.of(node) : List.of();
        } catch (Exception e) {
            log.warn("拉取指定 Issue #{} 异常: {}", cfg.issueNumber(), e.getMessage());
            return List.of();
        }
    }

    /** 构造带鉴权 / GitHub 版本头的 GET 请求（GitHub API 强制要求 User-Agent）。 */
    private HttpRequest newRequest(String url, String token) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "support-pilot")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET();
        if (token != null && !token.isBlank()) {
            b.header("Authorization", "Bearer " + token);
        }
        return b.build();
    }

    /** ISO-8601 → Instant；解析失败 / 为空兜底当前时间，保证 SLA 总有计时起点。 */
    private Instant parseInstant(String iso) {
        if (iso == null || iso.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(iso);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    /** 解析 "a=1,b=2" 形式的 label 映射为 Map<String,Long>。 */
    private static Map<String, Long> parseLongMap(String raw) {
        Map<String, Long> m = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return m;
        }
        for (String pair : raw.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && !kv[0].isBlank() && !kv[1].isBlank()) {
                try {
                    m.put(kv[0].trim(), Long.parseLong(kv[1].trim()));
                } catch (NumberFormatException ignored) {
                    // 非法值跳过
                }
            }
        }
        return m;
    }

    /** 解析 "a=P1,b=P2" 形式的 label 映射为 Map<String,String>。 */
    private static Map<String, String> parseStringMap(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return m;
        }
        for (String pair : raw.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && !kv[0].isBlank()) {
                m.put(kv[0].trim(), kv[1].trim());
            }
        }
        return m;
    }
}
