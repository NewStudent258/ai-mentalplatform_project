package org.example.aispingboot.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.example.aispingboot.common.Result;
import org.example.aispingboot.entity.Notification;
import org.example.aispingboot.service.NotificationService;
import org.example.aispingboot.util.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 站内通知接口。
 * <p>
 * 所有查询都限定为「当前用户自己的通知」，用户ID 一律从 token 取，
 * 不接受前端传入——否则可以构造参数读取他人的通知内容。
 */
@RestController
@RequestMapping("/api/notification")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    /** 我的通知列表 */
    @GetMapping("/my")
    public Result<List<Notification>> myNotifications(
            @RequestParam(required = false) Integer limit) {
        return Result.ok(notificationService.listByUser(getCurrentUserId(), limit));
    }

    /** 未读数（前端铃铛角标） */
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.ok(notificationService.countUnread(getCurrentUserId()));
    }

    /** 标记单条已读 */
    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(getCurrentUserId(), id);
        return Result.ok(null);
    }

    /** 全部标记已读 */
    @PostMapping("/read-all")
    public Result<Void> markAllRead() {
        notificationService.markAllRead(getCurrentUserId());
        return Result.ok(null);
    }

    private Long getCurrentUserId() {
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        return jwt.getClaim("userId").asLong();
    }
}
