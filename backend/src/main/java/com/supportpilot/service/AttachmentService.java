package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.common.UserContext;
import com.supportpilot.dto.AttachmentVO;
import com.supportpilot.entity.Attachment;
import com.supportpilot.entity.Ticket;
import com.supportpilot.entity.TicketMessage;
import com.supportpilot.entity.User;
import com.supportpilot.repository.AttachmentRepository;
import com.supportpilot.repository.TicketMessageRepository;
import com.supportpilot.repository.TicketRepository;
import com.supportpilot.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工单附件（V2.1 M4）：
 * 白名单 + 魔数双校验、UUID 存储名、单文件 10MB / 单工单 200MB、鉴权下载、180 天清理。
 */
@Slf4j
@Service
public class AttachmentService {

    public static final String VIS_CUSTOMER = "CUSTOMER";
    public static final String VIS_INTERNAL = "INTERNAL";

    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_TICKET_BYTES = 200L * 1024 * 1024;
    private static final int MAX_UPLOAD_PER_MINUTE = 30;
    private static final int CLEANUP_KEEP_DAYS = 180;

    private static final Map<String, String> EXT_MIME = new HashMap<>();
    private static final Set<String> TEXT_EXTS = Set.of("txt", "log", "csv", "json");

    static {
        EXT_MIME.put("png", "image/png");
        EXT_MIME.put("jpg", "image/jpeg");
        EXT_MIME.put("jpeg", "image/jpeg");
        EXT_MIME.put("gif", "image/gif");
        EXT_MIME.put("pdf", "application/pdf");
        EXT_MIME.put("txt", "text/plain");
        EXT_MIME.put("log", "text/plain");
        EXT_MIME.put("csv", "text/csv");
        EXT_MIME.put("zip", "application/zip");
        EXT_MIME.put("json", "application/json");
    }

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    private final AttachmentRepository attachmentRepository;
    private final TicketRepository ticketRepository;
    private final TicketMessageRepository ticketMessageRepository;
    private final UserRepository userRepository;
    private final Map<Long, Deque<Long>> uploadWindows = new ConcurrentHashMap<>();

    @Value("${app.attachment.dir:./data/attachments}")
    private String storageDir;

    @Value("${app.attachment.quota-bytes:5368709120}")
    private long quotaBytes;

    public AttachmentService(AttachmentRepository attachmentRepository,
                             TicketRepository ticketRepository,
                             TicketMessageRepository ticketMessageRepository,
                             UserRepository userRepository) {
        this.attachmentRepository = attachmentRepository;
        this.ticketRepository = ticketRepository;
        this.ticketMessageRepository = ticketMessageRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public AttachmentVO upload(Long ticketId, MultipartFile file, Long messageId, String visibility) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> BusinessException.notFound("工单不存在"));
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("附件不能为空");
        }
        UserContext.CurrentUser me = UserContext.get();
        if (me != null) {
            rateLimit(me.getId());
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw BusinessException.badRequest("单个附件不能超过 10MB（当前 "
                    + sizeText(file.getSize()) + "）");
        }

        String original = sanitizeName(file.getOriginalFilename());
        String ext = extensionOf(original);
        if (!EXT_MIME.containsKey(ext)) {
            throw BusinessException.badRequest("不支持的文件类型，可上传 png/jpg/gif/pdf/txt/log/csv/zip/json");
        }

        long used = attachmentRepository.sumSizeByTicket(ticketId);
        if (used + file.getSize() > MAX_TICKET_BYTES) {
            throw BusinessException.badRequest("该工单附件已达 200MB 上限，请清理后再传");
        }

        if (messageId != null) {
            TicketMessage message = ticketMessageRepository.findById(messageId)
                    .orElseThrow(() -> BusinessException.badRequest("关联消息不存在"));
            if (message.getTicket() == null || !ticketId.equals(message.getTicket().getId())) {
                throw BusinessException.badRequest("关联消息不属于该工单");
            }
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(500, "附件读取失败，请重试");
        }
        if (!magicMatches(ext, bytes)) {
            throw BusinessException.badRequest("文件内容与扩展名不符，已拒绝上传");
        }

        String sha256 = sha256(bytes);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        long tenantKey = ticket.getTenant() == null || ticket.getTenant().getId() == null
                ? 0L : ticket.getTenant().getId();
        String key = tenantKey + "/" + ticketId + "/" + uuid + "." + ext;

        Path root = Paths.get(storageDir).toAbsolutePath().normalize();
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(500, "附件保存路径非法");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.copy(new java.io.ByteArrayInputStream(bytes), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("附件落盘失败 ticket={} key={}", ticketId, key, e);
            throw new BusinessException(500, "附件保存失败，请重试或联系管理员");
        }

        Attachment a = new Attachment();
        a.setTenantId(ticket.getTenant() == null ? null : ticket.getTenant().getId());
        a.setTicketId(ticketId);
        a.setMessageId(messageId);
        a.setUploaderId(me == null ? null : me.getId());
        a.setFileName(original);
        a.setExt(ext);
        a.setMime(resolveMime(ext, file.getContentType()));
        a.setSizeBytes((long) bytes.length);
        a.setSha256(sha256);
        a.setStorageKey(key);
        a.setVisibility(VIS_INTERNAL.equalsIgnoreCase(visibility) ? VIS_INTERNAL : VIS_CUSTOMER);
        a.setCreatedAt(Instant.now());
        try {
            attachmentRepository.save(a);
        } catch (RuntimeException e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // 回滚文件失败仅记录
            }
            throw e;
        }
        return toVO(a);
    }

    public Attachment requireAttachment(Long id) {
        return attachmentRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("附件不存在"));
    }

    /** 鉴权下载：调用方必须先校验工单数据范围。 */
    public Resource loadResource(Attachment a) {
        if (Boolean.TRUE.equals(a.getPurged())) {
            throw BusinessException.notFound("附件已清理");
        }
        Path root = Paths.get(storageDir).toAbsolutePath().normalize();
        Path target = root.resolve(a.getStorageKey()).normalize();
        if (!target.startsWith(root) || !Files.exists(target)) {
            throw BusinessException.notFound("附件文件不存在");
        }
        return new FileSystemResource(target);
    }

    public List<AttachmentVO> listByTicket(Long ticketId) {
        return attachmentRepository.findByTicketIdAndDeletedFalseAndPurgedFalseOrderByIdAsc(ticketId)
                .stream().map(this::toVO).toList();
    }

    public Map<Long, List<AttachmentVO>> listByMessages(List<Long> messageIds) {
        Map<Long, List<AttachmentVO>> map = new HashMap<>();
        if (messageIds == null || messageIds.isEmpty()) {
            return map;
        }
        for (Attachment a : attachmentRepository
                .findByMessageIdInAndDeletedFalseAndPurgedFalseOrderByIdAsc(messageIds)) {
            map.computeIfAbsent(a.getMessageId(), k -> new ArrayList<>()).add(toVO(a));
        }
        return map;
    }

    @Transactional
    public void delete(Long id) {
        Attachment a = requireAttachment(id);
        UserContext.CurrentUser me = UserContext.get();
        boolean owner = me != null && me.getId().equals(a.getUploaderId());
        boolean privileged = me != null && ("ADMIN".equals(me.getRole()) || "MANAGER".equals(me.getRole()));
        if (!owner && !privileged) {
            throw new BusinessException(403, "只有上传人或管理员可以删除附件");
        }
        a.setDeleted(true);
        attachmentRepository.save(a);
    }

    @Transactional
    public AttachmentVO changeVisibility(Long id, String visibility) {
        Attachment a = requireAttachment(id);
        if (!VIS_INTERNAL.equalsIgnoreCase(visibility) && !VIS_CUSTOMER.equalsIgnoreCase(visibility)) {
            throw BusinessException.badRequest("可见性必须是 CUSTOMER 或 INTERNAL");
        }
        a.setVisibility(VIS_INTERNAL.equalsIgnoreCase(visibility) ? VIS_INTERNAL : VIS_CUSTOMER);
        attachmentRepository.save(a);
        return toVO(a);
    }

    /** 把暂存在工单主体的附件绑定到刚创建的消息上，并跟随消息的可见性。 */
    @Transactional
    public void bindToMessage(Long ticketId, Long messageId, List<Long> attachmentIds, boolean internal) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        for (Long id : attachmentIds) {
            Attachment a = attachmentRepository.findByIdAndDeletedFalse(id).orElse(null);
            if (a == null || !ticketId.equals(a.getTicketId())) {
                continue;
            }
            a.setMessageId(messageId);
            a.setVisibility(internal ? VIS_INTERNAL : VIS_CUSTOMER);
            attachmentRepository.save(a);
        }
    }

    /** 清理预览：关闭满 180 天的附件（FR-F05）。 */
    public Map<String, Object> cleanupPreview() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(CLEANUP_KEEP_DAYS));
        List<Attachment> items = attachmentRepository
                .findPurgeCandidates(cutoff, PageRequest.of(0, 500));
        long bytes = items.stream().mapToLong(Attachment::getSizeBytes).sum();
        List<AttachmentVO> vos = items.stream().map(this::toVO).toList();
        Map<String, Object> result = new HashMap<>();
        result.put("count", attachmentRepository.countPurgeCandidates(cutoff));
        result.put("previewCount", vos.size());
        result.put("bytes", bytes);
        result.put("sizeText", sizeText(bytes));
        result.put("items", vos);
        return result;
    }

    /** 执行清理：物理删除文件，记录保留并标记 purged=1（FR-F05）。 */
    @Transactional
    public Map<String, Object> cleanupExecute() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(CLEANUP_KEEP_DAYS));
        List<Attachment> items = attachmentRepository
                .findPurgeCandidates(cutoff, PageRequest.of(0, 500));
        Path root = Paths.get(storageDir).toAbsolutePath().normalize();
        long freed = 0;
        for (Attachment a : items) {
            try {
                Path target = root.resolve(a.getStorageKey()).normalize();
                if (target.startsWith(root)) {
                    Files.deleteIfExists(target);
                }
            } catch (IOException e) {
                log.warn("附件物理删除失败 id={} key={}", a.getId(), a.getStorageKey(), e);
            }
            a.setPurged(true);
            a.setDeleted(true);
            freed += a.getSizeBytes();
        }
        attachmentRepository.saveAll(items);
        Map<String, Object> result = new HashMap<>();
        result.put("purged", items.size());
        result.put("freedBytes", freed);
        result.put("freedText", sizeText(freed));
        return result;
    }

    /** 每日 03:10 统计总占用，超过 70% 记录告警（FR-F05）。 */
    @Scheduled(cron = "0 10 3 * * ?")
    public void dailyStats() {
        long total = attachmentRepository.sumAllSize();
        log.info("附件总量统计：{}（配额 {}）", sizeText(total), sizeText(quotaBytes));
        if (quotaBytes > 0 && total > quotaBytes * 7 / 10) {
            log.warn("附件容量已使用 {}，超过配额 70%，请及时清理", sizeText(total));
        }
    }

    public long totalSize() {
        return attachmentRepository.sumAllSize();
    }

    public AttachmentVO toVO(Attachment a) {
        AttachmentVO vo = new AttachmentVO();
        vo.setId(a.getId());
        vo.setTicketId(a.getTicketId());
        vo.setMessageId(a.getMessageId());
        vo.setFileName(a.getFileName());
        vo.setExt(a.getExt());
        vo.setMime(a.getMime());
        vo.setSizeBytes(a.getSizeBytes());
        vo.setSizeText(sizeText(a.getSizeBytes()));
        vo.setUploaderId(a.getUploaderId());
        if (a.getUploaderId() != null) {
            User u = userRepository.findById(a.getUploaderId()).orElse(null);
            vo.setUploaderName(u == null ? null : u.getName());
        }
        vo.setVisibility(a.getVisibility());
        vo.setCreatedAt(a.getCreatedAt() == null ? null : TIME_FMT.format(a.getCreatedAt()));
        return vo;
    }

    private void rateLimit(Long userId) {
        long now = System.currentTimeMillis();
        Deque<Long> window = uploadWindows.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > 60_000) {
                window.pollFirst();
            }
            if (window.size() >= MAX_UPLOAD_PER_MINUTE) {
                throw new BusinessException(429, "上传过于频繁，请稍后再试");
            }
            window.addLast(now);
        }
    }

    private String sanitizeName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "attachment";
        }
        String name = raw.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}\\/:*?\"<>|]", "_").trim();
        if (name.isBlank()) {
            return "attachment";
        }
        if (name.length() > 120) {
            String ext = extensionOf(name);
            String base = name.substring(0, Math.max(1, 120 - ext.length() - 1));
            name = base + "." + ext;
        }
        return name;
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String resolveMime(String ext, String declared) {
        String expected = EXT_MIME.get(ext);
        if (declared != null && !declared.isBlank() && !"application/octet-stream".equalsIgnoreCase(declared)) {
            if (declared.equalsIgnoreCase(expected)
                    || ("jpg".equals(ext) || "jpeg".equals(ext)) && "image/jpeg".equalsIgnoreCase(declared)) {
                return declared;
            }
        }
        return expected;
    }

    /** 魔数校验：只做类别比对，避免浏览器 MIME 不一致导致误拒。 */
    private boolean magicMatches(String ext, byte[] b) {
        if (b.length == 0) {
            return false;
        }
        return switch (ext) {
            case "png" -> b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            case "jpg", "jpeg" -> b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
            case "gif" -> b.length > 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8';
            case "pdf" -> b.length > 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
            case "zip" -> b.length > 4 && b[0] == 'P' && b[1] == 'K'
                    && ((b[2] == 3 && b[3] == 4) || (b[2] == 5 && b[3] == 6));
            case "json" -> firstNonWhitespace(b) == '{' || firstNonWhitespace(b) == '[';
            default -> TEXT_EXTS.contains(ext) && !looksBinary(b);
        };
    }

    private char firstNonWhitespace(byte[] b) {
        for (byte v : b) {
            char c = (char) (v & 0xFF);
            if (!Character.isWhitespace(c)) {
                return c;
            }
        }
        return 0;
    }

    private boolean looksBinary(byte[] b) {
        int limit = Math.min(b.length, 1024);
        for (int i = 0; i < limit; i++) {
            if (b[i] == 0) {
                return true;
            }
        }
        return false;
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder(64);
            for (byte v : digest) {
                sb.append(String.format("%02x", v));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BusinessException(500, "附件校验失败");
        }
    }

    public static String sizeText(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /** 供上传接口在落库前后校验工单所属，避免跨租户写入。 */
    public Ticket requireTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> BusinessException.notFound("工单不存在"));
    }
}
