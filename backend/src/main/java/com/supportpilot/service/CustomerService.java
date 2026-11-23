package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.common.TenantContext;
import com.supportpilot.dto.ContactVO;
import com.supportpilot.dto.CustomerDetailVO;
import com.supportpilot.dto.CustomerPageVO;
import com.supportpilot.dto.CustomerRequest;
import com.supportpilot.dto.CustomerVO;
import com.supportpilot.dto.TicketPageVO;
import com.supportpilot.dto.TicketVO;
import com.supportpilot.entity.Contact;
import com.supportpilot.entity.Customer;
import com.supportpilot.entity.Ticket;
import com.supportpilot.repository.ContactRepository;
import com.supportpilot.repository.CustomerRepository;
import com.supportpilot.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 客户域服务（V2.0 EP1）：客户 CRUD、租户内 code 唯一校验、关联工单删除守卫、
 * 客户 360 视图聚合（基本信息 + 联系人 + 分页工单 + 近 5 单 + 平均 CSAT + SLA 达成率）。
 *
 * <p>角色控制不在本层，由 Task 6 集中式拦截器统一处理；本服务仅做业务规则校验。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final ContactRepository contactRepository;
    private final TicketRepository ticketRepository;
    private final TicketService ticketService;
    private final SlaService slaService;

    /** 允许的服务等级枚举。 */
    private static final Set<String> ALLOWED_SLA_TIER = Set.of("金牌", "银牌", "铜牌", "默认");

    @Value("${app.tenant.default-id:1}")
    private Long defaultTenantId;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    /** 客户列表：支持名称 / 编码关键字 + 服务等级筛选，分页。 */
    public CustomerPageVO page(String keyword, String slaTier, int page, int size) {
        Long tid = TenantContext.get();
        String kw = blank(keyword) ? null : keyword.trim();
        String tier = (blank(slaTier) || "全部".equals(slaTier)) ? null : slaTier.trim();
        int pageIndex = Math.max(page - 1, 0);
        int pageSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<Customer> result = customerRepository.search(tid, tier, kw, pageable);
        List<Customer> content = result.getContent();
        Map<Long, Long> countMap = loadTicketCountMap(content);
        List<CustomerVO> list = content.stream()
                .map(c -> toVO(c, countMap.getOrDefault(c.getId(), 0L)))
                .toList();
        return new CustomerPageVO(list, result.getTotalElements());
    }

    /** 客户 360 详情：基本信息 + 联系人 + 分页工单 + 近 5 单 + 平均 CSAT + SLA 达成率。 */
    public CustomerDetailVO detail(Long id, int page, int size) {
        Customer c = load(id);
        CustomerDetailVO vo = new CustomerDetailVO();
        long total = ticketRepository.countByCustomerId(id);
        vo.setCustomer(toVO(c, total));
        vo.setTicketTotal(total);

        // 联系人：主联系人排在最前
        List<Contact> contacts = contactRepository.findByCustomerIdOrderByIdDesc(id);
        contacts.sort(Comparator.comparing((Contact x) -> !Boolean.TRUE.equals(x.getIsPrimary())));
        vo.setContacts(contacts.stream().map(CustomerService::toContactVO).toList());

        // 分页工单列表
        int pageIndex = Math.max(page - 1, 0);
        int pageSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<Ticket> tp = ticketRepository.findByCustomerId(id, pageable);
        List<TicketVO> ticketList = tp.getContent().stream().map(ticketService::toVO).toList();
        vo.setTickets(new TicketPageVO(ticketList, tp.getTotalElements()));

        // 近 5 条工单
        List<Ticket> recent = ticketRepository.findTop5ByCustomerIdOrderByIdDesc(id);
        vo.setRecentTickets(recent.stream().map(ticketService::toVO).toList());

        // 聚合：平均 CSAT + SLA 达成率（口径与 MetricsService.buildOps 一致）
        List<Ticket> all = ticketRepository.findByCustomerId(id);
        double csatSum = 0;
        int csatCount = 0;
        int slaMet = 0;
        int resolvedCount = 0;
        for (Ticket t : all) {
            if (t.getCsatScore() != null) {
                csatSum += t.getCsatScore();
                csatCount++;
            }
            if (t.getCreatedAt() != null && t.getResolvedAt() != null) {
                resolvedCount++;
                long paused = t.getSlaPausedSeconds() == null ? 0L : t.getSlaPausedSeconds();
                long elapsedSec = Math.max(0L,
                        Duration.between(t.getCreatedAt(), t.getResolvedAt()).getSeconds() - paused);
                long limitSec = (long) slaService.limitHoursFor(t.getPriority()) * 3600L;
                if (elapsedSec <= limitSec) {
                    slaMet++;
                }
            }
        }
        vo.setAvgCsat(csatCount == 0 ? 0 : Math.round(csatSum / csatCount * 10.0) / 10.0);
        vo.setCsatCount(csatCount);
        vo.setResolvedCount(resolvedCount);
        vo.setSlaRate(resolvedCount == 0 ? 0 : Math.round(slaMet * 1000.0 / resolvedCount) / 10.0);
        return vo;
    }

    @Transactional
    public CustomerVO create(CustomerRequest req) {
        Long tid = effectiveTenantId();
        String code = req.getCode().trim();
        if (customerRepository.existsByTenantIdAndCode(tid, code)) {
            throw BusinessException.badRequest("客户编码已存在");
        }
        Customer c = new Customer();
        c.setTenantId(tid);
        applyRequest(c, req);
        c.setCreatedAt(Instant.now());
        return toVO(customerRepository.save(c), 0L);
    }

    @Transactional
    public CustomerVO update(Long id, CustomerRequest req) {
        Customer c = load(id);
        Long tid = c.getTenantId() != null ? c.getTenantId() : effectiveTenantId();
        String code = req.getCode().trim();
        if (customerRepository.existsByTenantIdAndCodeAndIdNot(tid, code, id)) {
            throw BusinessException.badRequest("客户编码已存在");
        }
        applyRequest(c, req);
        return toVO(customerRepository.save(c), ticketRepository.countByCustomerId(id));
    }

    /** 停用客户（status 置为 disabled），不物理删除。 */
    @Transactional
    public CustomerVO disable(Long id) {
        Customer c = load(id);
        c.setStatus("disabled");
        return toVO(customerRepository.save(c), ticketRepository.countByCustomerId(id));
    }

    /**
     * 删除客户：存在关联工单时拒绝删除（AC-C01b），提示改为停用。
     * 无关联工单时级联删除其联系人。
     */
    @Transactional
    public void delete(Long id) {
        Customer c = load(id);
        if (ticketRepository.countByCustomerId(id) > 0) {
            throw BusinessException.badRequest("该客户存在关联工单，无法删除，请改为停用");
        }
        List<Contact> contacts = contactRepository.findByCustomerIdOrderByIdDesc(id);
        if (!contacts.isEmpty()) {
            contactRepository.deleteAll(contacts);
        }
        customerRepository.delete(c);
    }

    // ===== 内部方法 =====

    private Customer load(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("客户不存在"));
    }

    private Long effectiveTenantId() {
        Long tid = TenantContext.get();
        return tid != null ? tid : defaultTenantId;
    }

    private void applyRequest(Customer c, CustomerRequest req) {
        c.setName(req.getName().trim());
        c.setCode(req.getCode().trim());
        String tier = blank(req.getSlaTier()) ? "默认" : req.getSlaTier().trim();
        if (!ALLOWED_SLA_TIER.contains(tier)) {
            throw BusinessException.badRequest("非法的服务等级：" + tier);
        }
        c.setSlaTier(tier);
        c.setContractNo(blank(req.getContractNo()) ? null : req.getContractNo().trim());
        c.setContractStart(req.getContractStart());
        c.setContractEnd(req.getContractEnd());
        String status = blank(req.getStatus()) ? "active" : req.getStatus().trim();
        if (!Set.of("active", "disabled").contains(status)) {
            throw BusinessException.badRequest("非法的客户状态：" + status);
        }
        c.setStatus(status);
    }

    private Map<Long, Long> loadTicketCountMap(List<Customer> customers) {
        List<Long> ids = customers.stream().map(Customer::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : ticketRepository.countByCustomerIdIn(ids)) {
            if (row[0] != null) {
                map.put(((Number) row[0]).longValue(), row[1] == null ? 0L : ((Number) row[1]).longValue());
            }
        }
        return map;
    }

    private CustomerVO toVO(Customer c, long ticketCount) {
        CustomerVO vo = new CustomerVO();
        vo.setId(c.getId());
        vo.setTenantId(c.getTenantId());
        vo.setName(c.getName());
        vo.setCode(c.getCode());
        vo.setSlaTier(c.getSlaTier());
        vo.setContractNo(c.getContractNo());
        vo.setContractStart(c.getContractStart());
        vo.setContractEnd(c.getContractEnd());
        vo.setStatus(c.getStatus());
        vo.setCreatedAt(c.getCreatedAt() == null ? null : TIME_FMT.format(c.getCreatedAt()));
        vo.setTicketCount(ticketCount);
        return vo;
    }

    private static ContactVO toContactVO(Contact c) {
        ContactVO vo = new ContactVO();
        vo.setId(c.getId());
        vo.setCustomerId(c.getCustomerId());
        vo.setName(c.getName());
        vo.setEmail(c.getEmail());
        vo.setPhone(c.getPhone());
        vo.setTitle(c.getTitle());
        vo.setIsPrimary(c.getIsPrimary());
        vo.setCreatedAt(c.getCreatedAt() == null ? null : TIME_FMT.format(c.getCreatedAt()));
        return vo;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
