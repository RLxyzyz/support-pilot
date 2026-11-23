package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.common.TenantContext;
import com.supportpilot.dto.ReplyTemplateVO;
import com.supportpilot.entity.ReplyTemplate;
import com.supportpilot.repository.ReplyTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 快捷回复模板（#5）：按租户隔离；演示模式（tid 为 null）下读取全部模板。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReplyTemplateService {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    private final ReplyTemplateRepository replyTemplateRepository;

    public List<ReplyTemplateVO> list() {
        Long tid = TenantContext.get();
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        List<ReplyTemplate> rows = tid == null
                ? replyTemplateRepository.findAll(sort)
                : replyTemplateRepository.findByTenantId(tid, sort);
        return rows.stream().map(this::toVO).toList();
    }

    @Transactional
    public ReplyTemplateVO create(String name, String content, String category) {
        if (name == null || name.isBlank()) {
            throw BusinessException.badRequest("模板名称不能为空");
        }
        ReplyTemplate t = new ReplyTemplate();
        t.setTenantId(TenantContext.get());
        t.setName(name.trim());
        t.setContent(content == null ? "" : content);
        t.setCategory(category == null || category.isBlank() ? null : category.trim());
        t.setCreatedAt(Instant.now());
        return toVO(replyTemplateRepository.save(t));
    }

    @Transactional
    public ReplyTemplateVO update(Long id, String name, String content, String category) {
        ReplyTemplate t = requireOwn(id);
        if (name != null && !name.isBlank()) {
            t.setName(name.trim());
        }
        if (content != null) {
            t.setContent(content);
        }
        if (category != null) {
            t.setCategory(category.isBlank() ? null : category.trim());
        }
        return toVO(replyTemplateRepository.save(t));
    }

    @Transactional
    public void delete(Long id) {
        replyTemplateRepository.delete(requireOwn(id));
    }

    private ReplyTemplate requireOwn(Long id) {
        ReplyTemplate t = replyTemplateRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("模板不存在"));
        Long tid = TenantContext.get();
        if (tid != null && !tid.equals(t.getTenantId())) {
            throw BusinessException.badRequest("无权操作该模板");
        }
        return t;
    }

    private ReplyTemplateVO toVO(ReplyTemplate t) {
        ReplyTemplateVO vo = new ReplyTemplateVO();
        vo.setId(t.getId());
        vo.setName(t.getName());
        vo.setContent(t.getContent());
        vo.setCategory(t.getCategory());
        vo.setCreatedAt(t.getCreatedAt() == null ? null : FMT.format(t.getCreatedAt()));
        return vo;
    }
}
