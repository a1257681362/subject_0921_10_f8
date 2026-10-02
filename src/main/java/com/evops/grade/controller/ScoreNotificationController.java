package com.evops.grade.controller;

import com.evops.common.ApiResponse;
import com.evops.grade.entity.ScoreNotification;
import com.evops.grade.service.ScoreNotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 站内通知：复核结论、超窗关闭都在此可查。
 */
@RestController
@RequestMapping("/api/notifications")
public class ScoreNotificationController {

    private final ScoreNotificationService notificationService;

    public ScoreNotificationController(ScoreNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<List<ScoreNotification>> list(@RequestParam(required = false) String regNo,
                                                      @RequestParam(required = false, defaultValue = "false")
                                                      boolean unreadOnly) {
        return ApiResponse.ok(notificationService.list(regNo, unreadOnly));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<ScoreNotification> markRead(@PathVariable Long id) {
        return ApiResponse.ok(notificationService.markRead(id));
    }
}
