package com.supportpilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportpilot.dto.AgentStepVO;
import com.supportpilot.dto.TriageVO;
import com.supportpilot.entity.AgentRun;
import com.supportpilot.entity.Draft;
import com.supportpilot.entity.EvalCase;
import com.supportpilot.entity.Ticket;
import com.supportpilot.entity.TicketMessage;
import com.supportpilot.entity.User;
import com.supportpilot.rag.RagResult;
import com.supportpilot.rag.RagService;
import com.supportpilot.repository.AgentRunRepository;
import com.supportpilot.repository.DraftRepository;
import com.supportpilot.repository.EvalCaseRepository;
import com.supportpilot.repository.TicketRepository;
import com.supportpilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Agent 处理服务：以 SSE 流向前端推送思考过程 / 步骤 / 草稿 / 引用。
 *
 * <p>app.ai.mock-enabled 已强制为 false：任何环境都走真实大模型与真实 RAG；
 * 仅当真实模型调用失败（未配置 Key / 网络异常等）时，回退到本地规则引擎，
 * 按工单内容识别问题领域并生成对应的排查建议，保证不同问题得到不同回复。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    @Value("${app.ai.mock-enabled:false}")
    private boolean mockEnabled;

    /** AI 成本单价（元 / 千 tokens），与 MetricsService 共用同一配置。 */
    @Value("${app.ai.cost-per-1k-tokens:0.008}")
    private double costPer1k;

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final RagService ragService;
    private final TicketRepository ticketRepository;
    private final DraftRepository draftRepository;
    private final AgentRunRepository agentRunRepository;
    private final EvalCaseRepository evalCaseRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    private static final long TIMEOUT_MS = 120_000L;
    private static final List<String> CLOSED_STATUSES = List.of("已解决", "已升级");

    public SseEmitter stream(String ticketId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Thread.startVirtualThread(() -> {
            try {
                boolean handled = false;
                if (!mockEnabled) {
                    handled = tryRealStream(emitter, ticketId);
                }
                if (!handled) {
                    runFallbackScenario(emitter, ticketId);
                }
                emitter.complete();
            } catch (Exception e) {
                log.warn("Agent SSE 结束异常: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /**
     * 真实模型路径：未配置 / 调用失败时返回 false，由调用方回退本地规则兜底。
     * 同时完成：RAG 未命中写 eval_case、解析 usage 计算成本并写 agent_run / draft.cost。
     */
    private boolean tryRealStream(SseEmitter emitter, String ticketId) {
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder == null) {
            return false;
        }
        try {
            send(emitter, "thinking", Map.of("label", "正在读取工单并检索知识库…"));
            Ticket ticket = loadTicket(ticketId);
            RagResult rag = ragService.retrieve(ticketId);
            String context = rag.context();
            if (!rag.hit()) {
                recordRagMiss(ticket);
            }
            send(emitter, "thinking", Map.of("label", "正在调用大模型生成回复…"));
            String prompt = buildPrompt(ticket, context);

            ChatResponse response = builder.build().prompt().user(prompt).call().chatResponse();
            String answer = extractText(response);
            Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
            int total = totalTokens(usage);
            double costYuan = total * costPer1k / 1000.0;
            String costText = total + " tok · ¥" + String.format("%.3f", costYuan);

            // 草稿落库（含引用文档 id 与成本）+ agent_run 用量记录
            Long draftId = persistDraft(ticket, answer, rag.hitDocIds(), String.format("¥%.3f", costYuan));
            persistAgentRun(ticket, costYuan, usage, total);

            Thread.sleep(400);
            send(emitter, "step", new AgentStepVO("生成", "tag-gen", "实时", "已生成实时回复草稿"));
            send(emitter, "draft", Map.of(
                    "content", answer,
                    "cost", costText,
                    "draftId", draftId == null ? "" : String.valueOf(draftId)));
            send(emitter, "cites", Map.of("items", parseCites(context)));
            send(emitter, "done", Map.of("ticketId", ticketId == null ? "" : ticketId));
            return true;
        } catch (Exception e) {
            log.warn("真实 AI 调用失败，回退演示场景: {}", e.getMessage());
            return false;
        }
    }

    private static String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null
                || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    private static int totalTokens(Usage usage) {
        if (usage == null) {
            return 0;
        }
        if (usage.getTotalTokens() != null) {
            return usage.getTotalTokens();
        }
        int p = usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
        int c = usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
        return p + c;
    }

    /**
     * RAG 未命中时把客户问题记入 eval_case（按租户 upsert，count/weight 自增），
     * 供看板「未命中问题」展示与知识库补全。
     */
    private void recordRagMiss(Ticket ticket) {
        if (ticket == null || ticket.getTitle() == null || ticket.getTitle().isBlank()) {
            return;
        }
        try {
            Long tenantId = ticket.getTenant() != null ? ticket.getTenant().getId() : null;
            String qRaw = ticket.getTitle().trim();
            final String q = qRaw.length() > 512 ? qRaw.substring(0, 512) : qRaw;
            EvalCase ec = evalCaseRepository.findByQuestionAndTenant(q, tenantId).orElseGet(() -> {
                EvalCase n = new EvalCase();
                n.setQuestion(q);
                n.setCount(0);
                n.setWeight(1);
                n.setSource("rag_miss");
                n.setTenantId(tenantId);
                n.setCreatedAt(Instant.now());
                return n;
            });
            int c = (ec.getCount() == null ? 0 : ec.getCount()) + 1;
            ec.setCount(c);
            ec.setWeight(c);
            ec.setLastAskedAt(Instant.now());
            evalCaseRepository.save(ec);
        } catch (Exception e) {
            log.warn("记录 RAG 未命中失败: {}", e.getMessage());
        }
    }

    /** 记录一次真实模型调用的成本与 token 用量（失败不阻断 SSE）。 */
    private void persistAgentRun(Ticket ticket, double costYuan, Usage usage, int total) {
        if (ticket == null || ticket.getId() == null) {
            return;
        }
        try {
            AgentRun run = new AgentRun();
            run.setTicketId(ticket.getId());
            run.setStatus("success");
            run.setCost(String.format("¥%.3f", costYuan));
            run.setTokens(total);
            run.setPromptTokens(usage == null || usage.getPromptTokens() == null ? null : usage.getPromptTokens());
            run.setCompletionTokens(usage == null || usage.getCompletionTokens() == null ? null : usage.getCompletionTokens());
            run.setCreatedAt(Instant.now());
            agentRunRepository.save(run);
        } catch (Exception e) {
            log.warn("agent_run 落库失败 ticketId={}: {}", ticket.getId(), e.getMessage());
        }
    }

    /**
     * 把真实模型生成的回复落库到 draft 表：同工单最近一条未采纳（非 adopted）草稿则更新内容，
     * 否则新建一条 status=generated 的草稿。同时写入引用文档 id 与成本。
     * 返回草稿 id（落库失败返回 null，不阻断 SSE）。
     */
    private Long persistDraft(Ticket ticket, String content, List<Long> citedDocIds, String cost) {
        if (ticket == null || ticket.getId() == null || content == null || content.isBlank()) {
            return null;
        }
        try {
            Draft draft = draftRepository.findFirstByTicketIdOrderByIdDesc(ticket.getId())
                    .filter(d -> !"adopted".equals(d.getStatus())) // 不覆盖已采纳的历史草稿
                    .orElseGet(() -> {
                        Draft d = new Draft();
                        d.setTicketId(ticket.getId());
                        d.setCreatedAt(Instant.now());
                        return d;
                    });
            draft.setContent(content);
            draft.setStatus("generated");
            draft.setCost(cost);
            draft.setCitedDocIds(citedDocIds == null || citedDocIds.isEmpty()
                    ? null : String.join(",", citedDocIds.stream().map(String::valueOf).toList()));
            return draftRepository.save(draft).getId();
        } catch (Exception e) {
            log.warn("草稿落库失败 ticketId={}: {}", ticket.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * AI 分诊建议：基于工单内容建议优先级 / 分类，处理人按当前负载（未结工单数最少）分配。
     * 大模型调用失败时回退规则（默认 P3，分类沿用工单现有或留空，处理人按负载）。
     */
    public TriageVO triage(String ticketId) {
        Ticket ticket = loadTicket(ticketId);
        TriageVO vo = new TriageVO();
        String priority = "P3";
        String category = ticket != null && ticket.getCategory() != null ? ticket.getCategory() : "";
        String reasoning = "";

        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder != null && ticket != null) {
            try {
                ChatResponse resp = builder.build().prompt().user(buildTriagePrompt(ticket)).call().chatResponse();
                String text = extractText(resp);
                JsonNode node = objectMapper.readTree(extractJson(text));
                String p = node.path("priority").asText("");
                if (List.of("P1", "P2", "P3", "P4").contains(p)) {
                    priority = p;
                }
                String c = node.path("category").asText("");
                if (c != null && !c.isBlank()) {
                    category = c;
                }
                reasoning = node.path("reasoning").asText("");
            } catch (Exception e) {
                log.warn("AI 分诊调用失败，回退规则: {}", e.getMessage());
                reasoning = "AI 建议不可用，已按规则回退：默认 P3，处理人取当前负载最低者。";
            }
        }
        vo.setPriority(priority);
        vo.setCategory(category);
        vo.setReasoning(reasoning);

        Long assigneeId = pickLeastLoadedAssignee();
        vo.setAssigneeId(assigneeId);
        vo.setAssigneeName(assigneeName(assigneeId));
        return vo;
    }

    /** 取当前未结工单（已解决/已升级除外）最少的处理人；并列取 id 最小；无用户返回 null。 */
    private Long pickLeastLoadedAssignee() {
        Long bestId = null;
        long bestLoad = Long.MAX_VALUE;
        for (User u : userRepository.findAll()) {
            if (u.getId() == null) {
                continue;
            }
            long load = ticketRepository.countByAssigneeIdAndStatusNotIn(u.getId(), CLOSED_STATUSES);
            if (load < bestLoad || (load == bestLoad && (bestId == null || u.getId() < bestId))) {
                bestLoad = load;
                bestId = u.getId();
            }
        }
        return bestId;
    }

    private String assigneeName(Long id) {
        if (id == null) {
            return null;
        }
        return userRepository.findById(id)
                .map(u -> (u.getName() != null && !u.getName().isBlank()) ? u.getName() : u.getUsername())
                .orElse(null);
    }

    private String buildTriagePrompt(Ticket ticket) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是企业技术支持分诊专家。基于以下工单，输出一个 JSON 对象，仅输出 JSON，不要任何解释或多余文本。\n");
        sb.append("JSON 字段：priority（P1|P2|P3|P4）、category（分类名，简短）、reasoning（50 字内依据）。\n");
        sb.append("优先级规则：P1=全站不可用或生产严重故障；P2=核心功能不可用；P3=一般功能问题；P4=咨询/配置/小问题。\n\n");
        sb.append("【工单】\n");
        sb.append("标题：").append(ticket.getTitle() == null ? "" : ticket.getTitle()).append("\n");
        if (ticket.getPriority() != null) sb.append("当前优先级：").append(ticket.getPriority()).append("\n");
        if (ticket.getCategory() != null) sb.append("当前分类：").append(ticket.getCategory()).append("\n");
        if (ticket.getProduct() != null && ticket.getProduct().getName() != null)
            sb.append("产品：").append(ticket.getProduct().getName()).append("\n");
        if (ticket.getBody() != null && !ticket.getBody().isBlank())
            sb.append("描述：").append(ticket.getBody()).append("\n");
        return sb.toString();
    }

    /** 从模型输出中截取首个 '{' 到末个 '}' 的 JSON 子串（容错模型多输出说明文字）。 */
    private String extractJson(String text) {
        if (text == null) {
            return "{}";
        }
        int s = text.indexOf('{');
        int e = text.lastIndexOf('}');
        if (s < 0 || e < 0 || e <= s) {
            return "{}";
        }
        return text.substring(s, e + 1);
    }

    private Ticket loadTicket(String ticketId) {
        if (ticketId == null) {
            return null;
        }
        try {
            Long id = Long.valueOf(ticketId.replace("#", "").trim());
            return ticketRepository.findDetailById(id).orElse(null);
        } catch (Exception e) {
            log.debug("加载工单失败，ticketId={}: {}", ticketId, e.getMessage());
            return null;
        }
    }

    /** 把检索上下文按行拆成引用列表，供前端 cites 展示；无内容返回空列表。 */
    private List<String> parseCites(String context) {
        if (context == null || context.isBlank()) {
            return List.of();
        }
        return Arrays.stream(context.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private String buildPrompt(Ticket ticket, String context) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一名企业级技术支持工程师，请针对以下客户工单生成面向客户的正式回复。\n\n");

        if (ticket != null) {
            sb.append("【工单信息】\n");
            sb.append("工单号：#").append(ticket.getId()).append("\n");
            if (ticket.getTitle() != null) sb.append("标题：").append(ticket.getTitle()).append("\n");
            if (ticket.getTenant() != null && ticket.getTenant().getName() != null)
                sb.append("租户：").append(ticket.getTenant().getName()).append("\n");
            if (ticket.getProduct() != null && ticket.getProduct().getName() != null)
                sb.append("产品：").append(ticket.getProduct().getName()).append("\n");
            if (ticket.getPriority() != null) sb.append("优先级：").append(ticket.getPriority()).append("\n");
            if (ticket.getStatus() != null) sb.append("状态：").append(ticket.getStatus()).append("\n");
            if (ticket.getCategory() != null) sb.append("分类：").append(ticket.getCategory()).append("\n");
            sb.append("\n");
            if (ticket.getBody() != null && !ticket.getBody().isBlank()) {
                sb.append("【问题描述】\n").append(ticket.getBody()).append("\n\n");
            }
            if (ticket.getCode() != null && !ticket.getCode().isBlank()) {
                sb.append("【附加代码/配置】\n").append(ticket.getCode()).append("\n\n");
            }
            if (ticket.getMessages() != null && !ticket.getMessages().isEmpty()) {
                sb.append("【会话记录】\n");
                for (TicketMessage m : ticket.getMessages()) {
                    sb.append("- [").append(m.getRole() == null ? "" : m.getRole());
                    if (m.getWho() != null) sb.append(" ").append(m.getWho());
                    sb.append("] ").append(m.getText() == null ? "" : m.getText()).append("\n");
                }
                sb.append("\n");
            }
        } else {
            sb.append("【工单信息】（未取到工单详情，请基于已有信息回复）\n\n");
        }

        boolean hasContext = context != null && !context.isBlank();
        if (hasContext) {
            sb.append("【检索到的相关依据】\n").append(context).append("\n\n");
        }
        sb.append("要求：\n");
        sb.append("1. 先简要判断问题原因，再给出可执行的排查/解决步骤；\n");
        if (hasContext) {
            sb.append("2. 在对应步骤后用 [1] [2] 标注引用上述检索依据；\n");
            sb.append("3. 语气专业、礼貌，必要时向客户索取补充信息；不确定的内容不要编造。\n");
        } else {
            sb.append("2. 未检索到知识库依据，请直接基于工单内容与你的专业知识回复，不要以缺少上下文或资料为由拒答；\n");
            sb.append("3. 语气专业、礼貌，必要时向客户索取补充信息；不确定的内容不要编造。\n");
        }
        return sb.toString();
    }

    private boolean send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
            return true;
        } catch (IOException e) {
            log.debug("SSE 发送中断（客户端可能已断开）: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 本地兜底：真实模型不可用（未配置 Key / 调用失败）时，按工单标题、描述与分类做关键词识别，
     * 生成与问题领域匹配的排查建议。不同问题会得到不同回复，不再固定输出同一份预置答案。
     */
    private void runFallbackScenario(SseEmitter emitter, String ticketId) throws InterruptedException {
        Ticket ticket = loadTicket(ticketId);
        String title = ticket == null || ticket.getTitle() == null || ticket.getTitle().isBlank()
                ? "您反馈的问题" : ticket.getTitle().trim();
        Topic topic = detectTopic(ticket);
        String priority = ticket != null && ticket.getPriority() != null ? ticket.getPriority() : "P3";

        String draft = buildFallbackDraft(title, topic, ticket);

        AgentStepVO triageStep = new AgentStepVO("分诊", "tag-triage", "0.3s",
                "问题类型 <b>" + topic.label() + "</b> · 建议优先级 <b>" + priority
                        + "</b><br><span style=\"color:var(--muted)\">依据：工单内容命中「"
                        + topic.label() + "」关键词，由本地规则引擎判定。</span>");
        AgentStepVO retrieveStep = new AgentStepVO("检索", "tag-retrieve", "0.6s",
                "本次由本地规则引擎生成，未调用知识库检索");
        AgentStepVO checkStep = new AgentStepVO("校验", "tag-check", "0.9s",
                hasDetailedBody(ticket)
                        ? "工单信息较完整，已直接给出排查建议。"
                        : "信息完整性 <b>不足</b>，已在回复中向客户索取："
                        + String.join("、", topic.needInfo()));
        AgentStepVO genStep = new AgentStepVO("生成", "tag-gen", "1.2s",
                "已生成回复草稿 · 本地规则引擎（未连接大模型，配置 AI_API_KEY 后可切换为模型生成）");

        send(emitter, "thinking", Map.of("label", "正在读取工单与会话记录…"));
        Thread.sleep(300);
        send(emitter, "step", triageStep);
        send(emitter, "thinking", Map.of("label", "正在检索知识库…"));
        Thread.sleep(300);
        send(emitter, "step", retrieveStep);
        Thread.sleep(250);
        send(emitter, "step", checkStep);
        Thread.sleep(250);
        send(emitter, "step", genStep);
        Thread.sleep(350);
        send(emitter, "draft", Map.of("content", draft, "cost", "本地规则生成 · 未调用大模型"));
        send(emitter, "cites", Map.of("items", List.of()));
        send(emitter, "done", Map.of("ticketId", ticketId == null ? "" : ticketId));
    }

    private boolean hasDetailedBody(Ticket ticket) {
        return ticket != null && ticket.getBody() != null && ticket.getBody().trim().length() >= 20;
    }

    private String buildFallbackDraft(String title, Topic topic, Ticket ticket) {
        StringBuilder sb = new StringBuilder();
        sb.append("您好，\n\n");
        sb.append("感谢您的反馈。关于「").append(title).append("」，").append(topic.judgment()).append("\n\n");

        sb.append("可能原因：\n");
        int i = 1;
        for (String c : topic.causes()) {
            sb.append(i++).append(". ").append(c).append("\n");
        }

        sb.append("\n建议按以下步骤处理：\n");
        i = 1;
        for (String s : topic.steps()) {
            sb.append(i++).append(". ").append(s).append("\n");
        }

        if (ticket != null && ticket.getBody() != null && !ticket.getBody().isBlank()) {
            String body = ticket.getBody().trim();
            String brief = body.length() > 80 ? body.substring(0, 80) + "…" : body;
            sb.append("\n您描述的现象（").append(brief).append("）我们已纳入排查范围。\n");
        }

        sb.append("\n为便于进一步定位，麻烦补充以下信息：\n");
        for (String n : topic.needInfo()) {
            sb.append("- ").append(n).append("\n");
        }
        sb.append("\n收到补充信息后我们会继续跟进；如该问题影响面较大，请随时告知，我们会优先处理。");

        return sb.toString();
    }

    /** 按标题 + 描述 + 分类的关键词识别问题领域，未命中时使用通用模板。 */
    private Topic detectTopic(Ticket ticket) {
        if (ticket == null) {
            return DEFAULT_TOPIC;
        }
        String text = ((ticket.getTitle() == null ? "" : ticket.getTitle()) + " "
                + (ticket.getBody() == null ? "" : ticket.getBody()) + " "
                + (ticket.getCategory() == null ? "" : ticket.getCategory()))
                .toLowerCase(Locale.ROOT);
        for (TopicRule rule : TOPIC_RULES) {
            for (String keyword : rule.keywords()) {
                if (text.contains(keyword)) {
                    return rule.topic();
                }
            }
        }
        return DEFAULT_TOPIC;
    }

    private record Topic(String label, String judgment, List<String> causes,
                         List<String> steps, List<String> needInfo) {
    }

    private record TopicRule(String[] keywords, Topic topic) {
    }

    private static final Topic DEFAULT_TOPIC = new Topic(
            "通用技术问题",
            "需要结合具体环境与复现步骤进一步定位，暂无法直接判定单一原因。",
            List.of("环境或配置与预期不一致", "版本或依赖存在差异", "操作步骤与产品文档描述不一致"),
            List.of("确认问题出现的时间、影响范围与受影响用户数，判断是否与近期变更相关",
                    "收集完整报错信息（页面提示、日志、接口返回）并确认能否稳定复现",
                    "按最小范围复现问题，逐步排除配置、数据与网络因素",
                    "若仍无法定位，请提供复现步骤，我们会协助进一步分析"),
            List.of("问题发生时间与影响范围", "完整报错信息或截图", "可稳定复现的操作步骤"));

    private static final List<TopicRule> TOPIC_RULES = List.of(
            new TopicRule(new String[]{"登录", "登陆", "密码", "账号", "账户", "验证码", "权限", "认证", "授权", "sso", "401", "403"},
                    new Topic("账号与登录",
                            "初步判断与账号认证或权限配置有关。",
                            List.of("账号状态异常（被锁定、过期或未授权）", "认证服务或单点登录配置发生变更", "浏览器缓存 / Cookie 残留导致会话失效"),
                            List.of("确认影响范围：是个别账号还是全部用户，是否与近期权限或组织架构调整相关",
                                    "请用户清理浏览器缓存并使用无痕窗口重试，换设备交叉验证",
                                    "核对账号状态、角色权限与认证服务日志，确认是否存在 401/403 记录",
                                    "如为单点登录场景，检查回调地址与证书是否在有效期内"),
                            List.of("受影响账号或用户 ID", "完整报错提示或截图", "问题发生时间及近期是否有权限变更"))),
            new TopicRule(new String[]{"支付", "订单", "退款", "退费", "计费", "发票", "金额", "扣费", "对账", "结算"},
                    new Topic("支付与计费",
                            "初步判断与交易、计费或对账链路有关。",
                            List.of("交易状态与订单状态不一致（回调延迟或丢失）", "计费规则或套餐配置与合同不一致", "第三方支付渠道返回异常"),
                            List.of("提供异常订单号，核对订单、支付流水与回调日志的时间线",
                                    "确认该笔交易的支付渠道返回码及是否收到异步通知",
                                    "核对计费规则配置与合同条款，确认是否存在配置偏差",
                                    "若为重复扣费或金额差异，先冻结相关订单并同步财务复核"),
                            List.of("异常订单号或交易流水号", "发生时间与金额", "支付渠道及返回的错误信息"))),
            new TopicRule(new String[]{"性能", "慢", "卡顿", "超时", "响应时间", "cpu", "内存", "并发", "延迟", "负载", "timeout"},
                    new Topic("性能与超时",
                            "初步判断与系统性能或资源瓶颈有关。",
                            List.of("请求数据量或并发量超出当前配置承载能力", "慢查询或未命中索引导致数据库响应变慢", "下游依赖或网络链路延迟升高"),
                            List.of("确认问题发生时间段，并对照监控查看 CPU、内存、数据库连接数与慢查询",
                                    "记录可复现的请求参数与耗时，判断是否为特定数据量或特定接口",
                                    "检查近期是否有数据量增长、索引变更或依赖升级",
                                    "必要时先做限流 / 扩容缓解，再定位根因"),
                            List.of("发生时间与影响范围", "可复现的请求或操作步骤", "相关接口耗时与监控截图"))),
            new TopicRule(new String[]{"导出", "报表", "统计", "数据", "图表", "汇总", "excel", "csv", "看板"},
                    new Topic("数据与报表",
                            "初步判断与数据查询、统计口径或导出能力有关。",
                            List.of("数据量超出单次查询或导出的上限", "统计口径与筛选条件不一致", "数据同步延迟导致展示结果与预期不符"),
                            List.of("确认筛选条件、时间范围与统计口径是否与预期一致",
                                    "缩小时间范围或数据量后重试，判断是否与数据规模相关",
                                    "核对数据同步任务的完成时间，排除同步延迟",
                                    "导出失败时提供导出条件与数据量，便于定位限制点"),
                            List.of("使用的筛选条件与时间范围", "导出或查询的数据量级", "期望结果与实际结果的差异示例"))),
            new TopicRule(new String[]{"接口", "api", "集成", "回调", "对接", "webhook", "第三方", "openapi", "签名"},
                    new Topic("接口与集成",
                            "初步判断与接口调用或第三方系统对接有关。",
                            List.of("接口参数、鉴权信息或签名与约定不一致", "回调地址不可达或响应超时", "双方数据格式或版本存在差异"),
                            List.of("提供完整的请求与响应报文（注意脱敏），核对状态码与错误码",
                                    "确认鉴权 token / 签名是否有效，是否已过期或被轮换",
                                    "检查回调地址的公网可达性与白名单配置",
                                    "对照接口文档核对字段类型、必填项与版本号"),
                            List.of("请求 URL、请求参数与响应报文", "调用时间与调用方环境", "接口文档版本或对接协议"))),
            new TopicRule(new String[]{"部署", "配置", "环境", "启动", "安装", "升级", "迁移", "发布", "端口", "镜像", "容器"},
                    new Topic("部署与配置",
                            "初步判断与部署环境或配置项有关。",
                            List.of("配置文件缺少必要参数或取值与当前环境不匹配", "依赖服务未就绪或端口被占用", "升级后版本兼容性发生变化"),
                            List.of("核对配置文件与目标环境的差异（地址、端口、密钥、开关）",
                                    "查看启动日志中的首个 ERROR 或异常堆栈，定位失败环节",
                                    "确认依赖服务已就绪、端口未被占用、权限与磁盘空间充足",
                                    "如为升级引发，先回滚验证是否为版本行为变化"),
                            List.of("部署方式与环境信息", "启动日志中的错误片段", "近期是否做过升级或配置变更"))),
            new TopicRule(new String[]{"网络", "连接", "502", "503", "504", "断连", "dns", "证书", "ssl", "代理"},
                    new Topic("网络与可用性",
                            "初步判断与网络链路或服务可用性有关。",
                            List.of("服务实例异常或负载均衡健康检查失败", "DNS 解析、证书或代理配置异常", "链路抖动或安全策略拦截"),
                            List.of("确认影响范围是个别地域、个别用户还是全部访问",
                                    "从客户端侧执行连通性测试（ping / telnet / curl）并记录结果",
                                    "检查服务健康状态、证书有效期与安全组 / 白名单策略",
                                    "对比故障前后是否有网络或发布变更"),
                            List.of("报错的具体地址与端口", "影响范围（地域 / 用户比例）", "故障开始时间与网络环境"))),
            new TopicRule(new String[]{"如何", "怎么", "请问", "咨询", "使用", "配置方法", "教程", "文档"},
                    new Topic("产品使用咨询",
                            "这是一个使用或配置类咨询，可按标准操作路径处理。",
                            List.of("对功能入口或操作路径不熟悉", "配置项含义与预期理解不一致", "权限不足导致部分功能不可见"),
                            List.of("说明当前使用的功能模块与具体操作目标",
                                    "对照产品文档确认操作路径与所需权限",
                                    "如涉及配置项，请按文档示例逐项核对取值",
                                    "仍无法完成时，可提供页面截图以便我们给出针对性指引"),
                            List.of("使用的功能模块", "期望达成的操作结果", "当前页面截图或提示信息"))));

}
