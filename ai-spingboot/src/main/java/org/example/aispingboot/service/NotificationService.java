package org.example.aispingboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.example.aispingboot.entity.Notification;
import org.example.aispingboot.entity.User;
import org.example.aispingboot.mapper.NotificationMapper;
import org.example.aispingboot.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内通知服务。
 * <p>
 * <b>设计原则：通知失败绝不能影响主业务。</b>
 * 工单已经建好了，若因为发通知抛异常导致整个建单事务回滚，
 * 那才是真正的灾难。因此本服务的所有推送动作都吞掉异常。
 * <p>
 * <b>同时支持两种渠道</b>：站内信（写入数据库，用户下次打开时可见）+ 邮件（
 * 见 {@link MailNotifier}，未配置 SMTP 时优雅降级）。
 * 站内信保证「一定会留下记录」，邮件负责「主动触达」。
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final MailNotifier mailNotifier;

    public NotificationService(NotificationMapper notificationMapper,
                               UserMapper userMapper,
                               MailNotifier mailNotifier) {
        this.notificationMapper = notificationMapper;
        this.userMapper = userMapper;
        this.mailNotifier = mailNotifier;
    }

    /**
     * 向某个角色下的所有用户推送通知。
     * <p>
     * 辅导员与管理员都是「一类人共同分担」的角色，因此不指定具体接收人，
     * 而是发给该角色下的全部成员——谁先看到谁处理。
     *
     * @param roleType 接收人角色（1 学生 / 2 管理员 / 3 辅导员）
     */
    public void notifyRole(Integer roleType, String type, String title, String content, Long bizId) {
        try {
            List<User> receivers = userMapper.selectList(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getUserType, roleType)
                            .eq(User::getStatus, 1));
            if (receivers.isEmpty()) {
                log.warn("角色 {} 下没有可用接收人，通知未送达：{}", roleType, title);
                return;
            }
            for (User receiver : receivers) {
                push(receiver, type, title, content, bizId);
            }
            log.info("已向 {} 名角色 {} 用户推送通知：{}", receivers.size(), roleType, title);
        } catch (Exception e) {
            // 通知失败不影响主业务：工单已建好，稍后仍可在列表中看到
            log.error("推送角色通知失败（不影响主业务）：{}", e.getMessage(), e);
        }
    }

    /** 向单个用户推送通知（站内信 + 邮件） */
    private void push(User receiver, String type, String title, String content, Long bizId) {
        try {
            Notification notification = Notification.builder()
                    .userId(receiver.getId())
                    .type(type)
                    .title(title)
                    .content(content)
                    .bizId(bizId)
                    .isRead(0)
                    .createdAt(LocalDateTime.now())
                    .build();
            notificationMapper.insert(notification);
        } catch (Exception e) {
            log.error("站内信写入失败（用户 {}）：{}", receiver.getId(), e.getMessage());
        }
        // 邮件属于「主动触达」渠道，失败已在其内部降级处理
        mailNotifier.send(receiver.getEmail(), title, content);
    }

    /** 查询某用户的通知（最新在前） */
    public List<Notification> listByUser(Long userId, Integer limit) {
        int size = (limit == null || limit <= 0) ? 20 : Math.min(limit, 100);
        return notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreatedAt)
                        .last("limit " + size));
    }

    /** 未读通知数（前端铃铛角标） */
    public long countUnread(Long userId) {
        return notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0));
    }

    /** 标记单条已读 */
    public void markRead(Long userId, Long notificationId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, notificationId)
                // 限定 user_id：防止通过构造 ID 把别人的通知标记为已读
                .eq(Notification::getUserId, userId)
                .set(Notification::getIsRead, 1));
    }

    /** 全部标记为已读 */
    public void markAllRead(Long userId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
    }
}
