package com.supportpilot.controller;

import com.supportpilot.common.Result;
import com.supportpilot.dto.NotificationPageVO;
import com.supportpilot.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 站内信接口（V2.0 EP3）。所有已登录用户只能读写自己的通知。
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public Result<NotificationPageVO> list(@RequestParam(required = false) String type,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return Result.ok(notificationService.page(type, page, size));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        return Result.ok(Map.of("count", notificationService.unreadCount()));
    }

    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return Result.ok();
    }

    @PostMapping("/read-all")
    public Result<Map<String, Integer>> markAllRead() {
        return Result.ok(Map.of("updated", notificationService.markAllRead()));
    }
}
