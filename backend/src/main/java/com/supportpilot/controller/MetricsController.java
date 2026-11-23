package com.supportpilot.controller;

import com.supportpilot.common.Result;
import com.supportpilot.dto.BoardVO;
import com.supportpilot.service.MetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsService metricsService;

    /**
     * 看板概览。
     *
     * @param days  统计天数（默认 7，仅在未传 start/end 时生效）
     * @param start 自定义起始时间（ISO-8601，可空）
     * @param end   自定义结束时间（ISO-8601，可空，默认当前时间）
     */
    @GetMapping("/overview")
    public Result<BoardVO> overview(@RequestParam(required = false) Integer days,
                                    @RequestParam(required = false) String start,
                                    @RequestParam(required = false) String end) {
        return Result.ok(metricsService.overview(days, parseInstant(start), parseInstant(end)));
    }

    private Instant parseInstant(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
