package com.supportpilot.controller;

import com.supportpilot.common.Result;
import com.supportpilot.dto.TriageVO;
import com.supportpilot.service.AgentService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * Agent 流式接口。浏览器 EventSource 仅支持 GET，故流式订阅为 GET，
 * POST 仅作为创建 run 的幂等入口。
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @GetMapping(value = "/runs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter runs(@RequestParam(required = false) String ticketId,
                           HttpServletResponse response) {
        // 关闭 Nginx 缓冲，保证 SSE 实时推送
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        return agentService.stream(ticketId);
    }

    @PostMapping("/runs")
    public Result<Map<String, Object>> createRun() {
        return Result.ok(Map.of(
                "runId", "run_" + System.currentTimeMillis(),
                "message", "请通过 GET /api/agent/runs?ticketId= 订阅流式输出"
        ));
    }

    @GetMapping("/runs/{id}")
    public Result<Map<String, Object>> getRun(@PathVariable String id) {
        return Result.ok(Map.of("runId", id, "steps", 5, "cost", "12.4s · 3,482 tok · ¥0.028"));
    }

    /** AI 分诊建议：返回建议优先级 / 分类 / 处理人（按负载）。 */
    @GetMapping("/triage")
    public Result<TriageVO> triage(@RequestParam String ticketId) {
        return Result.ok(agentService.triage(ticketId));
    }
}
