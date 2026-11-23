package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.common.DisplayUtil;
import com.supportpilot.common.NotifyType;
import com.supportpilot.common.Role;
import com.supportpilot.common.TenantContext;
import com.supportpilot.common.UserContext;
import com.supportpilot.dto.BackfillCustomerRequest;
import com.supportpilot.dto.CreateTicketRequest;
import com.supportpilot.dto.RelatedTicketVO;
import com.supportpilot.dto.TicketMessageVO;
import com.supportpilot.dto.TicketPageVO;
import com.supportpilot.dto.TicketQuery;
import com.supportpilot.dto.TicketVO;
import com.supportpilot.entity.Contact;
import com.supportpilot.entity.Customer;
import com.supportpilot.entity.Product;
import com.supportpilot.entity.Tenant;
import com.supportpilot.entity.Ticket;
import com.supportpilot.entity.TicketMessage;
import com.supportpilot.entity.TicketRelation;
import com.supportpilot.entity.User;
import com.supportpilot.repository.ContactRepository;
import com.supportpilot.repository.CustomerRepository;
import com.supportpilot.repository.ProductRepository;
import com.supportpilot.repository.TenantRepository;
import com.supportpilot.repository.TicketMessageRepository;
import com.supportpilot.repository.TicketRelationRepository;
import com.supportpilot.repository.TicketRepository;
import com.supportpilot.repository.TicketSimilarityRepository;
import com.supportpilot.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TenantRepository tenantRepository;
    private final ProductRepository productRepository;
    private final NotificationDispatcher notificationDispatcher;
    private final UserRepository userRepository;
    private final TicketMessageRepository ticketMessageRepository;
    private final TicketRelationRepository ticketRelationRepository;
    private final CustomerRepository customerRepository;
    private final ContactRepository contactRepository;
    private final TicketAssignmentService ticketAssignmentService;
    private final KbService kbService;
    private final SlaService slaService;
    private final SlaPolicyService slaPolicyService;
    private final AttachmentService attachmentService;
    private final OperationLogService operationLogService;
    private final SimilarityService similarityService;
    private final L0Service l0Service;
    private final SentimentService sentimentService;
    private final MailService mailService;
    private final SummaryService summaryService;
    private final TicketSimilarityRepository ticketSimilarityRepository;

    /** 允许的工单状态集合（用于改状态校验）。 */
    private static final Set<String> ALLOWED_STATUS = Set.of("待分诊", "处理中", "待客户", "已解决", "已升级");
    /** 允许的优先级集合。 */
    private static final Set<String> ALLOWED_PRIORITY = Set.of("P1", "P2", "P3", "P4");

    @Value("${app.tenant.default-id:1}")
    private Long defaultTenantId;

    /** 演示/回滚开关：关闭后 RBAC 降级为旧鉴权（角色校验与数据范围均放行）。 */
    @Value("${app.rbac.enabled:true}")
    private boolean rbacEnabled;

    public TicketPageVO page(TicketQuery q, int page, int size) {
        Specification<Ticket> spec = buildSpec(q);
        int pageIndex = Math.max(page - 1, 0);
        int pageSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<Ticket> result = ticketRepository.findAll(spec, pageable);
        List<Ticket> content = result.getContent();
        Map<Long, User> userMap = loadUserMap(content);
        Map<Long, Customer> customerMap = loadCustomerMap(content);
        Map<Long, Contact> contactMap = loadContactMap(content);
        List<TicketVO> list = content.stream()
                .map(t -> baseVO(t, userMap, customerMap, contactMap)).toList();
        return new TicketPageVO(list, result.getTotalElements());
    }

    /** 导出符合筛选条件的全部工单为 CSV（带 BOM 兼容 Excel 中文）。 */
    public String exportCsv(TicketQuery q) {
        Specification<Ticket> spec = buildSpec(q);
        List<Ticket> all = ticketRepository.findAll(spec, Pageable.unpaged())
                .stream().toList();
        Map<Long, User> userMap = loadUserMap(all);
        Map<Long, Customer> customerMap = loadCustomerMap(all);
        Map<Long, Contact> contactMap = loadContactMap(all);
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"编号", "标题", "客户", "产品", "优先级", "状态", "分类",
                "处理人", "SLA", "创建时间", "首次响应", "解决时间", "更新时间"});
        for (Ticket t : all) {
            TicketVO vo = baseVO(t, userMap, customerMap, contactMap);
            rows.add(new String[]{
                    vo.getId(), vo.getTitle(),
                    vo.getCustomerName() == null ? "" : vo.getCustomerName(),
                    vo.getProduct(),
                    vo.getPriority(), vo.getStatus(), vo.getCategory(),
                    vo.getAssigneeName() == null ? "" : vo.getAssigneeName(),
                    vo.getSla(),
                    vo.getCreatedAt() == null ? "" : vo.getCreatedAt(),
                    vo.getFirstResponseAt() == null ? "" : vo.getFirstResponseAt(),
                    vo.getResolvedAt() == null ? "" : vo.getResolvedAt(),
                    vo.getUpdateTime() == null ? "" : vo.getUpdateTime()
            });
        }
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // BOM：Excel 正确识别 UTF-8 中文
        for (String[] r : rows) {
            sb.append(String.join(",", java.util.Arrays.stream(r).map(TicketService::csvQuote).toList()));
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String csvQuote(String v) {
        if (v == null) {
            return "";
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    /** 构建列表 / 导出共用的动态查询条件。 */
    private Specification<Ticket> buildSpec(TicketQuery q) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            Long tid = TenantContext.get();
            if (tid != null) {
                preds.add(cb.equal(root.get("tenant").get("id"), tid));
            }
            if (q.status() != null && !q.status().isBlank() && !"全部".equals(q.status())) {
                preds.add(cb.equal(root.get("status"), q.status()));
            }
            if (q.priority() != null && !q.priority().isBlank() && !"全部".equals(q.priority())) {
                preds.add(cb.equal(root.get("priority"), q.priority()));
            }
            if (q.assigneeId() != null) {
                preds.add(cb.equal(root.get("assigneeId"), q.assigneeId()));
            }
            if (q.productId() != null) {
                preds.add(cb.equal(root.get("product").get("id"), q.productId()));
            }
            if (q.keyword() != null && !q.keyword().isBlank()) {
                String kw = "%" + q.keyword().toLowerCase() + "%";
                preds.add(cb.or(
                        cb.like(cb.lower(root.get("title")), kw),
                        cb.like(cb.lower(root.get("body")), kw)
                ));
            }
            if (q.start() != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), q.start()));
            }
            if (q.end() != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("createdAt"), q.end()));
            }
            if (Boolean.TRUE.equals(q.overdue())) {
                preds.add(overduePredicate(root, cb));
            }
            // V2.0 EP1：仅看未归户存量工单（PRD EX-03）
            if (Boolean.TRUE.equals(q.unassigned())) {
                preds.add(cb.isNull(root.get("customerId")));
            }
            // ===== V2.0 EP2 数据范围过滤（SELF / TEAM；演示 tid=null 放宽为 ALL）=====
            String scope = effectiveScope();
            if (!Role.SCOPE_ALL.equals(scope)) {
                Set<Long> allowed = allowedAssigneeIds(scope);
                if (allowed.isEmpty()) {
                    preds.add(cb.disjunction()); // 无可见处理人 → 空结果
                } else {
                    preds.add(root.get("assigneeId").in(allowed));
                }
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
    }

    /** 演示模式（tid=null）或 RBAC 开关关闭时放宽为 ALL，否则取当前用户数据范围。 */
    private String effectiveScope() {
        if (!rbacEnabled || TenantContext.get() == null) {
            return Role.SCOPE_ALL;
        }
        return UserContext.getDataScope();
    }

    /** SELF → {本人}；TEAM → {本人 + 同组成员}；其它（ALL）由调用方短路。 */
    private Set<Long> allowedAssigneeIds(String scope) {
        Set<Long> ids = new HashSet<>();
        UserContext.CurrentUser u = UserContext.get();
        if (u == null) {
            return ids;
        }
        ids.add(u.getId());
        if (Role.SCOPE_TEAM.equals(scope) && u.getTeamId() != null) {
            userRepository.findByTeamId(u.getTeamId()).stream().map(User::getId).forEach(ids::add);
        }
        return ids;
    }

    /** 详情访问校验：SELF / TEAM 范围下只能查看分配给本人 / 本组的工单，否则 403。 */
    private void checkDataScopeAccess(Ticket ticket) {
        String scope = effectiveScope();
        if (Role.SCOPE_ALL.equals(scope)) {
            return;
        }
        Set<Long> allowed = allowedAssigneeIds(scope);
        if (ticket.getAssigneeId() == null || !allowed.contains(ticket.getAssigneeId())) {
            throw new BusinessException(403, "无权限查看该工单");
        }
    }

    /**
     * 超时谓词：非「已解决/已升级/待客户」状态，且创建时间早于「当前时间 - 优先级时限」。
     * 注意：暂未在谓词中扣除历史暂停秒数（极少数长暂停场景可能误判），展示层 SLA 仍精确扣减。
     */
    private Predicate overduePredicate(jakarta.persistence.criteria.Root<Ticket> root,
                                       jakarta.persistence.criteria.CriteriaBuilder cb) {
        Instant now = Instant.now();
        List<Predicate> priPreds = new ArrayList<>();
        for (String p : new String[]{"P1", "P2", "P3", "P4"}) {
            Instant threshold = now.minusSeconds((long) slaService.limitHoursFor(p) * 3600L);
            priPreds.add(cb.and(
                    cb.equal(root.get("priority"), p),
                    cb.lessThan(root.get("createdAt"), threshold)
            ));
        }
        // 优先级为空 / 其它值按 P4 兜底
        Instant p4 = now.minusSeconds((long) slaService.limitHoursFor("P4") * 3600L);
        priPreds.add(cb.and(
                cb.not(root.get("priority").in("P1", "P2", "P3", "P4")),
                cb.lessThan(root.get("createdAt"), p4)
        ));
        return cb.and(
                cb.not(root.get("status").in("已解决", "已升级", "待客户")),
                cb.or(priPreds.toArray(new Predicate[0]))
        );
    }

    /** 快速统计：侧边栏角标、工单列表 sub 文案用。 */
    public Map<String, Object> quickStats() {
        Long tid = TenantContext.get();
        long total = ticketRepository.countAll(tid);
        long p1Open = ticketRepository.countByPriorityAndStatusNot(tid, "P1", "已解决");
        long processing = ticketRepository.countByStatus(tid, "处理中");
        long pending = ticketRepository.countByStatus(tid, "待分诊")
                + ticketRepository.countByStatus(tid, "待客户");
        return Map.of(
                "total", total,
                "p1Open", p1Open,
                "processing", processing,
                "pending", pending
        );
    }

    /** 批量加载本页工单涉及的处理人，避免列表 N+1 查询。 */
    private Map<Long, User> loadUserMap(List<Ticket> tickets) {
        Set<Long> ids = tickets.stream()
                .map(Ticket::getAssigneeId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, User> map = new HashMap<>();
        userRepository.findAllById(ids).forEach(u -> map.put(u.getId(), u));
        return map;
    }

    /** 批量加载本页工单涉及的客户，避免列表 N+1 查询。 */
    private Map<Long, Customer> loadCustomerMap(List<Ticket> tickets) {
        Set<Long> ids = tickets.stream()
                .map(Ticket::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Customer> map = new HashMap<>();
        customerRepository.findAllById(ids).forEach(c -> map.put(c.getId(), c));
        return map;
    }

    /** 批量加载本页工单涉及的联系人，避免列表 N+1 查询。 */
    private Map<Long, Contact> loadContactMap(List<Ticket> tickets) {
        Set<Long> ids = tickets.stream()
                .map(Ticket::getContactId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Contact> map = new HashMap<>();
        contactRepository.findAllById(ids).forEach(c -> map.put(c.getId(), c));
        return map;
    }

    public TicketVO get(String rawId) {
        Long id = parseId(rawId);
        Ticket ticket = ticketRepository.findDetailById(id)
                .orElseThrow(() -> BusinessException.notFound("工单不存在"));
        checkDataScopeAccess(ticket);
        TicketVO vo = baseVO(ticket, null, null, null);
        if (ticket.getMessages() != null) {
            List<Long> messageIds = ticket.getMessages().stream()
                    .map(TicketMessage::getId).filter(java.util.Objects::nonNull).toList();
            var attachmentsByMessage = attachmentService.listByMessages(messageIds);
            String latestSentiment = null;
            for (TicketMessage m : ticket.getMessages()) {
                if (m.getSentiment() != null) {
                    latestSentiment = m.getSentiment();
                }
            }
            vo.setSentiment(latestSentiment);
            vo.setMessages(ticket.getMessages().stream().map(m -> {
                TicketMessageVO mvo = new TicketMessageVO();
                mvo.setId(m.getId());
                mvo.setWho(m.getWho());
                mvo.setRole(m.getRole());
                mvo.setText(m.getText());
                mvo.setInternal(Boolean.TRUE.equals(m.getInternal()));
                mvo.setAttachments(attachmentsByMessage.getOrDefault(m.getId(), List.of()));
                mvo.setSentiment(m.getSentiment());
                mvo.setSentimentIntensity(m.getSentimentIntensity());
                return mvo;
            }).toList());
        }
        vo.setRelations(buildRelations(ticket.getId()));
        vo.setAttachments(attachmentService.listByTicket(id).stream()
                .filter(a -> a.getMessageId() == null).toList());
        vo.setSummary(summaryService.summaryText(id));
        return vo;
    }

    /** 供附件等子资源接口复用：加载工单并校验数据范围。 */
    public Ticket requireVisibleTicket(Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("工单不存在"));
        checkDataScopeAccess(ticket);
        return ticket;
    }

    @Transactional
    public TicketVO create(CreateTicketRequest req) {
        Ticket t = buildTicket(req);
        ticketRepository.save(t);
        // V2.3 旁路增强：相似归组 → L0 自动应答；失败不影响建单
        try {
            similarityService.detectAndGroup(t);
        } catch (Exception e) {
            log.warn("[相似归组] 工单 #{} 检测失败：{}", t.getId(), e.getMessage());
        }
        try {
            l0Service.attemptAutoResolve(t);
        } catch (Exception e) {
            log.warn("[L0] 工单 #{} 自动应答失败：{}", t.getId(), e.getMessage());
        }
        return baseVO(t, null, null, null);
    }

    /** 批量导入：逐条创建，标题为空或优先级非法的条目跳过不计入。 */
    @Transactional
    public Map<String, Object> importAll(List<CreateTicketRequest> reqs) {
        int imported = 0;
        int skipped = 0;
        if (reqs == null || reqs.isEmpty()) {
            return Map.of("imported", 0, "skipped", 0);
        }
        for (CreateTicketRequest req : reqs) {
            if (req == null || req.getTitle() == null || req.getTitle().isBlank()
                    || (req.getPriority() != null && !ALLOWED_PRIORITY.contains(req.getPriority()))) {
                skipped++;
                continue;
            }
            try {
                // V2.0 EP1：导入渠道客户自动匹配（FR-C03），未命中则落为「未归户」工单
                Long customerId = resolveImportCustomer(req);
                Ticket t = buildTicket(req, customerId, true);
                // 自动派单：按支持人员当前持单量均分
                t.setAssigneeId(ticketAssignmentService.pickLeastLoaded(
                        t.getTenant() == null ? null : t.getTenant().getId()));
                ticketRepository.save(t);
                if (t.getAssigneeId() != null) {
                    // 自动派单同样发「分派」通知：站内信 + 已开启的邮件通道
                    notificationDispatcher.dispatchTicketEvent(
                            NotifyType.TICKET_ASSIGNED, t, t.getAssigneeId(), "系统自动派单");
                }
                imported++;
            } catch (BusinessException e) {
                // 单条非法（如联系人不属于客户）只跳过该条，不影响整批
                skipped++;
            }
        }
        return Map.of("imported", imported, "skipped", skipped);
    }

    /** 导入渠道客户自动匹配（FR-C03）：显式 ID > 联系人邮箱 > 客户名称；均未命中返回 null（未归户）。 */
    private Long resolveImportCustomer(CreateTicketRequest req) {
        if (req.getCustomerId() != null) {
            return req.getCustomerId();
        }
        Long tid = TenantContext.get();
        String email = req.getContactEmail();
        if (email != null && !email.isBlank()) {
            for (Contact c : contactRepository.findByEmailIgnoreCase(email.trim())) {
                Customer cu = customerRepository.findById(c.getCustomerId()).orElse(null);
                if (cu != null && (tid == null || tid.equals(cu.getTenantId()))) {
                    return cu.getId();
                }
            }
        }
        String name = req.getCustomerName();
        if (name != null && !name.isBlank()) {
            for (Customer cu : customerRepository.findByNameIgnoreCase(name.trim())) {
                if (tid == null || tid.equals(cu.getTenantId())) {
                    return cu.getId();
                }
            }
        }
        return null;
    }

    /**
     * 存量工单批量归户（PRD EX-03 / RK-01）：仅处理 customer_id 为空的工单，
     * 已挂客户的工单计入 skipped，避免误覆盖。
     */
    @Transactional
    public Map<String, Object> backfillCustomer(BackfillCustomerRequest req) {
        if (req == null || req.getTicketIds() == null || req.getTicketIds().isEmpty()) {
            throw BusinessException.badRequest("请选择待归户工单");
        }
        Long customerId = req.getCustomerId();
        if (customerId == null) {
            throw BusinessException.badRequest("请选择目标客户");
        }
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> BusinessException.badRequest("所选客户不存在"));
        Long contactId = null;
        if (req.getContactId() != null) {
            Contact contact = contactRepository.findById(req.getContactId())
                    .orElseThrow(() -> BusinessException.badRequest("所选联系人不存在"));
            if (!customer.getId().equals(contact.getCustomerId())) {
                throw BusinessException.badRequest("联系人不属于所选客户");
            }
            contactId = contact.getId();
        }
        int updated = 0;
        int skipped = 0;
        for (Long id : new LinkedHashSet<>(req.getTicketIds())) {
            Ticket t = id == null ? null : ticketRepository.findById(id).orElse(null);
            if (t == null || t.getCustomerId() != null) {
                skipped++;
                continue;
            }
            t.setCustomerId(customer.getId());
            t.setContactId(contactId);
            t.setUpdateTime("刚刚");
            ticketRepository.save(t);
            updated++;
        }
        return Map.of("updated", updated, "skipped", skipped);
    }

    /** 构建一条工单实体（不负责 save，供单建与批量导入复用）。 */
    private Ticket buildTicket(CreateTicketRequest req) {
        // V2.0 EP1：手工建单必须挂客户（AC-C03a）
        if (req.getCustomerId() == null) {
            throw BusinessException.badRequest("请选择所属客户");
        }
        return buildTicket(req, req.getCustomerId(), false);
    }

    /**
     * @param resolvedCustomerId 已解析的客户 ID（导入渠道可能为 null）
     * @param unassignedAllowed  是否允许落为「未归户」工单（仅导入渠道）
     */
    private Ticket buildTicket(CreateTicketRequest req, Long resolvedCustomerId, boolean unassignedAllowed) {
        Customer customer = null;
        Long contactId = null;
        if (resolvedCustomerId == null) {
            if (!unassignedAllowed) {
                throw BusinessException.badRequest("请选择所属客户");
            }
            if (req.getContactId() != null) {
                throw BusinessException.badRequest("请先选择所属客户");
            }
        } else {
            customer = customerRepository.findById(resolvedCustomerId)
                    .orElseThrow(() -> BusinessException.badRequest("所选客户不存在"));
            if (req.getContactId() != null) {
                Contact contact = contactRepository.findById(req.getContactId())
                        .orElseThrow(() -> BusinessException.badRequest("所选联系人不存在"));
                if (!customer.getId().equals(contact.getCustomerId())) {
                    throw BusinessException.badRequest("联系人不属于所选客户");
                }
                contactId = contact.getId();
            }
        }

        Long nextId = ticketRepository.findTopByOrderByIdDesc()
                .map(t -> t.getId() + 1)
                .orElse(41000L);
        Long tenantId = req.getTenantId() != null ? req.getTenantId() : defaultTenantId;
        Long productId = req.getProductId() != null ? req.getProductId() : 1L;
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        Product product = productRepository.findById(productId).orElse(null);

        String priority = "P3";
        if (req.getPriority() != null && !req.getPriority().isBlank()) {
            priority = req.getPriority();
        }
        String category = (req.getCategory() != null && !req.getCategory().isBlank()) ? req.getCategory() : "未分类";

        Ticket t = new Ticket();
        t.setId(nextId);
        t.setTenant(tenant);
        t.setProduct(product);
        t.setCustomerId(customer == null ? null : customer.getId());
        t.setContactId(contactId);
        t.setTitle(req.getTitle());
        t.setBody(req.getBody() == null ? "" : req.getBody());
        t.setCode("");
        t.setPriority(priority);
        t.setPriorityClass(DisplayUtil.priorityClass(priority));
        t.setStatus("待分诊");
        t.setStatusClass(DisplayUtil.statusClass("待分诊"));
        t.setCategory(category);
        t.setCreatedAt(Instant.now()); // SLA 计时起点
        // V2.1 FR-S02：创建时写入 SLA 快照（策略、到期时间、日历），后续策略变更不影响本单
        slaPolicyService.applySnapshot(t);
        t.setUpdateTime("刚刚");
        return t;
    }

    /** 接单：兼容旧入口，内部走并发安全的认领逻辑。 */
    @Transactional
    public TicketVO accept(String rawId) {
        return claim(rawId);
    }

    /**
     * 并发认领（V2.1 M3 FR-C01/C02/C03）：
     * 行级悲观锁保证同一工单只有一人认领成功；已归属他人返回 409。
     */
    @Transactional
    public TicketVO claim(String rawId) {
        UserContext.CurrentUser me = requireUser();
        Long id = parseId(rawId);
        Ticket t = ticketRepository.findByIdForUpdate(id)
                .orElseThrow(() -> BusinessException.notFound("工单不存在"));
        checkDataScopeAccess(t);

        if (me.getId().equals(t.getAssigneeId())) {
            return get(rawId); // 本人重复认领，幂等返回
        }
        if ("已解决".equals(t.getStatus()) || "已升级".equals(t.getStatus())) {
            throw new BusinessException(409, "工单已结束，无法认领");
        }
        if (t.getAssigneeId() != null) {
            String name = userRepository.findById(t.getAssigneeId())
                    .map(this::displayName).orElse("其他成员");
            auditClaimFail(t, me, name);
            throw new BusinessException(409, "该工单已被 " + name + " 认领");
        }

        String oldStatus = t.getStatus();
        t.setAssigneeId(me.getId());
        if (t.getFirstResponseAt() == null) {
            t.setFirstResponseAt(Instant.now()); // 首次响应时间（FRT 指标）
        }
        t.setStatus("处理中");
        t.setStatusClass(DisplayUtil.statusClass("处理中"));
        t.setUpdateTime("刚刚");
        slaService.onStatusChange(t, oldStatus, "处理中");
        if (SlaPolicyService.STATE_WARNING.equals(t.getSlaState())) {
            t.setSlaState(SlaPolicyService.STATE_RUNNING);
        }
        ticketRepository.save(t);
        return get(rawId);
    }

    private void auditClaimFail(Ticket t, UserContext.CurrentUser me, String holderName) {
        try {
            com.supportpilot.common.audit.AuditContext ctx = new com.supportpilot.common.audit.AuditContext(
                    me.getId(), me.getName(), me.getUsername(), TenantContext.get(),
                    "ticket", "claim", "ticket", String.valueOf(t.getId()),
                    "认领冲突：工单已归属 " + holderName);
            operationLogService.record(ctx, null, null, null, false,
                    "该工单已被 " + holderName + " 认领", 0);
        } catch (Exception ignored) {
            // 审计失败不影响冲突返回
        }
    }

    /** 转派：将处理人改为目标用户，并留一条内部备注便于追溯。 */
    @Transactional
    public TicketVO assign(String rawId, Long assigneeId) {
        if (assigneeId == null) {
            throw BusinessException.badRequest("目标处理人不能为空");
        }
        User target = userRepository.findById(assigneeId)
                .orElseThrow(() -> BusinessException.badRequest("目标用户不存在"));
        Ticket t = loadTicket(rawId);
        Long previousAssignee = t.getAssigneeId();
        t.setAssigneeId(target.getId());
        t.setUpdateTime("刚刚");
        ticketRepository.save(t);
        appendSystemMessage(t, "工单已转派给 " + displayName(target));
        // V2.0 EP3：分派 / 转派通知（首次分派=ASSIGNED，已有处理人=TRANSFERRED；派给自己不发）
        if (previousAssignee == null || !previousAssignee.equals(target.getId())) {
            String eventType = previousAssignee == null ? NotifyType.TICKET_ASSIGNED : NotifyType.TICKET_TRANSFERRED;
            UserContext.CurrentUser me = UserContext.get();
            notificationDispatcher.dispatchTicketEvent(
                    eventType, t, target.getId(), me == null ? null : me.getName());
        }
        return get(rawId);
    }

    /** 升级 L2：状态置为「已升级」，SLA 停止计时。 */
    @Transactional
    public TicketVO escalate(String rawId) {
        Ticket t = loadTicket(rawId);
        String oldStatus = t.getStatus();
        t.setStatus("已升级");
        t.setStatusClass(DisplayUtil.statusClass("已升级"));
        t.setUpdateTime("刚刚");
        slaService.onStatusChange(t, oldStatus, "已升级");
        slaPolicyService.markStopped(t);
        ticketRepository.save(t);
        return get(rawId);
    }

    /** 通用修改工单状态。 */
    @Transactional
    public TicketVO updateStatus(String rawId, String status) {
        if (status == null || status.isBlank()) {
            throw BusinessException.badRequest("状态不能为空");
        }
        if (!ALLOWED_STATUS.contains(status)) {
            throw BusinessException.badRequest("非法状态：" + status);
        }
        Ticket t = loadTicket(rawId);
        String oldStatus = t.getStatus();
        Long ticketId = t.getId();
        t.setStatus(status);
        t.setStatusClass(DisplayUtil.statusClass(status));
        t.setUpdateTime("刚刚");
        slaService.onStatusChange(t, oldStatus, status);
        // 解决时间维护：置「已解决」时写入并结算 SLA；从「已解决」改回时清空并重新计时
        if ("已解决".equals(status)) {
            if (t.getResolvedAt() == null) {
                t.setResolvedAt(Instant.now());
            }
            slaPolicyService.markResolved(t);
        } else if ("已解决".equals(oldStatus)) {
            t.setResolvedAt(null);
            slaPolicyService.resetOnReopen(t);
        }
        if ("已升级".equals(status)) {
            slaPolicyService.markStopped(t);
        }
        ticketRepository.save(t);
        if ("已解决".equals(status)) {
            // 解决后异步沉淀进知识库（事务提交后执行），失败不影响主流程
            kbService.ingestTicketAsync(ticketId);
        }
        return get(rawId);
    }

    /** 修改工单优先级（P1-P4）。 */
    @Transactional
    public TicketVO updatePriority(String rawId, String priority) {
        if (priority == null || priority.isBlank()) {
            throw BusinessException.badRequest("优先级不能为空");
        }
        if (!ALLOWED_PRIORITY.contains(priority)) {
            throw BusinessException.badRequest("非法优先级：" + priority);
        }
        Ticket t = loadTicket(rawId);
        t.setPriority(priority);
        t.setPriorityClass(DisplayUtil.priorityClass(priority));
        t.setUpdateTime("刚刚");
        ticketRepository.save(t);
        return get(rawId);
    }

    /** 修改工单分类（供 AI 分诊一键应用 / 手工归类）。 */
    @Transactional
    public TicketVO updateCategory(String rawId, String category) {
        if (category == null || category.isBlank()) {
            throw BusinessException.badRequest("分类不能为空");
        }
        if (category.length() > 64) {
            throw BusinessException.badRequest("分类过长");
        }
        Ticket t = loadTicket(rawId);
        t.setCategory(category.trim());
        t.setUpdateTime("刚刚");
        ticketRepository.save(t);
        return get(rawId);
    }

    /** 提交（代录）客户满意度评价：score 1-5，comment 选填。 */
    @Transactional
    public TicketVO csat(String rawId, Integer score, String comment) {
        if (score == null || score < 1 || score > 5) {
            throw BusinessException.badRequest("评分需在 1-5 之间");
        }
        Ticket t = loadTicket(rawId);
        t.setCsatScore(score);
        t.setCsatComment(comment == null ? null : comment.trim());
        t.setCsatAt(Instant.now());
        t.setUpdateTime("刚刚");
        ticketRepository.save(t);
        return get(rawId);
    }

    /** 新增工单关联（双向可见）。relatedTicketId 为对方工单的数字 id。 */
    @Transactional
    public List<RelatedTicketVO> addRelation(String rawId, Long relatedTicketId, String relationType) {
        Long ticketId = parseId(rawId);
        if (relatedTicketId == null || relatedTicketId.equals(ticketId)) {
            throw BusinessException.badRequest("关联工单无效");
        }
        ticketRepository.findById(relatedTicketId)
                .orElseThrow(() -> BusinessException.badRequest("关联工单不存在"));
        String type = (relationType == null || relationType.isBlank()) ? "related" : relationType.trim();
        TicketRelation rel = new TicketRelation();
        rel.setTicketId(ticketId);
        rel.setRelatedTicketId(relatedTicketId);
        rel.setRelationType(type);
        rel.setCreatedAt(Instant.now());
        ticketRelationRepository.save(rel);
        return buildRelations(ticketId);
    }

    /** 取消关联（仅允许删除与当前工单相关的关联记录）。 */
    @Transactional
    public List<RelatedTicketVO> removeRelation(String rawId, Long relationId) {
        Long ticketId = parseId(rawId);
        TicketRelation rel = ticketRelationRepository.findById(relationId)
                .orElseThrow(() -> BusinessException.notFound("关联记录不存在"));
        if (!ticketId.equals(rel.getTicketId()) && !ticketId.equals(rel.getRelatedTicketId())) {
            throw BusinessException.badRequest("无权操作该关联");
        }
        ticketRelationRepository.delete(rel);
        return buildRelations(ticketId);
    }

    /** 查询工单的关联列表（双向：ticket_id 或 related_ticket_id 命中）。 */
    public List<RelatedTicketVO> listRelations(String rawId) {
        return buildRelations(parseId(rawId));
    }

    /** 构建关联工单 VO：取对端工单标题/状态，relationType 取记录值。 */
    private List<RelatedTicketVO> buildRelations(Long ticketId) {
        List<TicketRelation> rows = ticketRelationRepository.findByTicketIdOrRelatedTicketId(ticketId, ticketId);
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> peerIds = rows.stream()
                .map(r -> ticketId.equals(r.getTicketId()) ? r.getRelatedTicketId() : r.getTicketId())
                .collect(Collectors.toSet());
        Map<Long, Ticket> peerMap = new HashMap<>();
        for (Ticket p : ticketRepository.findAllById(peerIds)) {
            peerMap.put(p.getId(), p);
        }
        List<RelatedTicketVO> list = new ArrayList<>();
        for (TicketRelation r : rows) {
            Long peerId = ticketId.equals(r.getTicketId()) ? r.getRelatedTicketId() : r.getTicketId();
            Ticket peer = peerMap.get(peerId);
            RelatedTicketVO vo = new RelatedTicketVO();
            vo.setRelationId(r.getId());
            vo.setId(peer == null ? ("#" + peerId) : ("#" + peer.getId()));
            vo.setTitle(peer == null ? "(已删除)" : peer.getTitle());
            vo.setStatus(peer == null ? "—" : peer.getStatus());
            vo.setRelationType(r.getRelationType());
            list.add(vo);
        }
        return list;
    }

    /** 为工单追加一条消息：客户回复或内部备注（internal=true 仅内部可见）。 */
    @Transactional
    public TicketVO addMessage(String rawId, String text, Boolean internal) {
        return addMessage(rawId, text, internal, null);
    }

    /** 追加消息并绑定附件（V2.1 M4）：内部备注的附件默认仅内部可见。 */
    @Transactional
    public TicketVO addMessage(String rawId, String text, Boolean internal, List<Long> attachmentIds) {
        if ((text == null || text.isBlank())
                && (attachmentIds == null || attachmentIds.isEmpty())) {
            throw BusinessException.badRequest("消息内容不能为空");
        }
        UserContext.CurrentUser me = requireUser();
        Ticket t = loadTicket(rawId);
        TicketMessage m = new TicketMessage();
        m.setTicket(t);
        m.setWho(displayName(me));
        m.setRole(roleLabel(me));
        m.setText(text == null ? "" : text);
        m.setInternal(Boolean.TRUE.equals(internal));
        ticketMessageRepository.save(m);
        if (attachmentIds != null && !attachmentIds.isEmpty()) {
            attachmentService.bindToMessage(t.getId(), m.getId(), attachmentIds, Boolean.TRUE.equals(internal));
        }
        t.getMessages().add(m); // 维护双向关联，保证同事务内 get() 返回的 VO 含新消息
        t.setUpdateTime("刚刚");
        ticketRepository.save(t);
        // 回复客户（非内部备注）时同步发邮件到联系人邮箱
        if (!Boolean.TRUE.equals(internal)) {
            sendReplyToCustomer(t, m.getText());
        }
        // V2.3 M5：情绪分析（旁路，失败不影响发消息）
        try {
            sentimentService.analyzeAndRecord(t, m, m.getText());
        } catch (Exception e) {
            log.warn("[情绪分析] 工单 #{} 消息分析失败：{}", t.getId(), e.getMessage());
        }
        return get(rawId);
    }

    /** 回复客户时同步发邮件到工单联系人邮箱；联系人或邮箱缺失则跳过，异常不影响发消息。 */
    private void sendReplyToCustomer(Ticket t, String text) {
        try {
            if (t.getContactId() == null) {
                log.info("[邮件] 工单 #{} 无关联联系人，跳过客户回复邮件", t.getId());
                return;
            }
            Contact contact = contactRepository.findById(t.getContactId()).orElse(null);
            if (contact == null || contact.getEmail() == null || contact.getEmail().isBlank()) {
                log.info("[邮件] 工单 #{} 联系人缺少邮箱，跳过客户回复邮件", t.getId());
                return;
            }
            String subject = "【工单回复】#" + t.getId() + " " + (t.getTitle() == null ? "" : t.getTitle());
            mailService.enqueueExternal(contact.getEmail(), subject, text);
            log.info("[邮件] 工单 #{} 回复已生成客户邮件任务：{}", t.getId(), contact.getEmail());
        } catch (Exception e) {
            log.warn("[邮件] 工单 #{} 客户回复邮件生成失败（不影响主流程）：{}", t.getId(), e.getMessage());
        }
    }

    private Ticket loadTicket(String rawId) {
        Long id = parseId(rawId);
        return ticketRepository.findDetailById(id)
                .orElseThrow(() -> BusinessException.notFound("工单不存在"));
    }

    private UserContext.CurrentUser requireUser() {
        UserContext.CurrentUser u = UserContext.get();
        if (u == null) {
            throw new BusinessException(401, "未登录");
        }
        return u;
    }

    private String displayName(UserContext.CurrentUser u) {
        return u.getName() != null && !u.getName().isBlank() ? u.getName() : u.getUsername();
    }

    private String displayName(User u) {
        return u.getName() != null && !u.getName().isBlank() ? u.getName() : u.getUsername();
    }

    private String roleLabel(UserContext.CurrentUser u) {
        return "admin".equalsIgnoreCase(u.getRole()) ? "管理员" : "工程师";
    }

    /** 追加一条系统内部备注（转派等系统动作留痕）。 */
    private void appendSystemMessage(Ticket t, String text) {
        TicketMessage m = new TicketMessage();
        m.setTicket(t);
        m.setWho("系统");
        m.setRole("系统");
        m.setText(text);
        m.setInternal(true);
        ticketMessageRepository.save(m);
        t.getMessages().add(m); // 维护双向关联，保证同事务内 get() 返回的 VO 含新消息
    }

    /**
     * 工单 VO 组装。userMap / customerMap / contactMap 为批量预加载映射（列表场景避免 N+1），
     * 详情 / 单条场景传 null，内部回退单查。
     */
    private TicketVO baseVO(Ticket t, Map<Long, User> userMap,
                            Map<Long, Customer> customerMap, Map<Long, Contact> contactMap) {
        TicketVO vo = new TicketVO();
        vo.setId("#" + t.getId());
        vo.setTitle(t.getTitle());
        vo.setTenant(t.getTenant() != null ? t.getTenant().getName() : "");
        vo.setProduct(t.getProduct() != null ? t.getProduct().getName() : "");
        vo.setPriority(t.getPriority());
        vo.setPriorityClass(t.getPriorityClass());
        vo.setStatus(t.getStatus());
        vo.setStatusClass(t.getStatusClass());
        vo.setCategory(t.getCategory());
        // SLA 实时计算：按优先级时限 + 创建时间，已解决/已升级停止计时
        slaService.fillSla(vo, t);
        vo.setUpdateTime(t.getUpdateTime());
        vo.setBody(t.getBody());
        vo.setCode(t.getCode());
        vo.setEnv(t.getEnv());
        vo.setCreatedAt(formatTime(t.getCreatedAt()));
        vo.setFirstResponseAt(formatTime(t.getFirstResponseAt()));
        vo.setResolvedAt(formatTime(t.getResolvedAt()));
        vo.setCsatScore(t.getCsatScore());
        vo.setCsatComment(t.getCsatComment());
        vo.setCsatAt(formatTime(t.getCsatAt()));
        fillAssignee(vo, t, userMap);
        fillCustomer(vo, t, customerMap, contactMap);
        // V2.3 智能服务字段
        vo.setSimilarityGroupId(t.getSimilarityGroupId());
        vo.setSimilarCount(t.getSimilarityGroupId() == null ? 0
                : (int) ticketSimilarityRepository.countByGroupId(t.getSimilarityGroupId()));
        vo.setL0State(t.getL0State());
        vo.setL0ReopenCount(t.getL0ReopenCount());
        return vo;
    }

    /** 供客户 360 等外部服务复用的单条工单 VO 组装。 */
    public TicketVO toVO(Ticket t) {
        return baseVO(t, null, null, null);
    }

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    private String formatTime(Instant i) {
        return i == null ? null : TIME_FMT.format(i);
    }

    /** 填充处理人信息：列表走批量 userMap，详情/单条回退到单查。 */
    private void fillAssignee(TicketVO vo, Ticket t, Map<Long, User> userMap) {
        if (t.getAssigneeId() == null) {
            return;
        }
        vo.setAssigneeId(t.getAssigneeId());
        User u = userMap != null ? userMap.get(t.getAssigneeId()) : null;
        if (u == null) {
            u = userRepository.findById(t.getAssigneeId()).orElse(null);
        }
        if (u != null) {
            vo.setAssigneeName(u.getName());
            vo.setAssigneeUsername(u.getUsername());
        }
    }

    /** 填充客户 360 信息块：列表走批量 map，详情 / 单条回退单查。 */
    private void fillCustomer(TicketVO vo, Ticket t,
                              Map<Long, Customer> customerMap, Map<Long, Contact> contactMap) {
        if (t.getCustomerId() != null) {
            Customer c = customerMap != null ? customerMap.get(t.getCustomerId()) : null;
            if (c == null) {
                c = customerRepository.findById(t.getCustomerId()).orElse(null);
            }
            if (c != null) {
                vo.setCustomerId(c.getId());
                vo.setCustomerName(c.getName());
                vo.setCustomerCode(c.getCode());
                vo.setSlaTier(c.getSlaTier());
            }
        }
        if (t.getContactId() != null) {
            Contact ct = contactMap != null ? contactMap.get(t.getContactId()) : null;
            if (ct == null) {
                ct = contactRepository.findById(t.getContactId()).orElse(null);
            }
            if (ct != null) {
                vo.setContactId(ct.getId());
                vo.setContactName(ct.getName());
                vo.setContactEmail(ct.getEmail());
                vo.setContactPhone(ct.getPhone());
                vo.setContactTitle(ct.getTitle());
                vo.setContactIsPrimary(ct.getIsPrimary());
            }
        }
    }

    private Long parseId(String rawId) {
        try {
            return Long.valueOf(rawId.replace("#", "").trim());
        } catch (Exception e) {
            throw BusinessException.badRequest("无效的工单编号：" + rawId);
        }
    }
}
