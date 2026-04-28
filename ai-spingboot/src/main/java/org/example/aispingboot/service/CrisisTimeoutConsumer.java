package org.example.aispingboot.service;

import com.rabbitmq.client.Channel;
import org.example.aispingboot.config.RabbitMQConfig;
import org.example.aispingboot.entity.CrisisWorkOrder;
import org.example.aispingboot.entity.Notification;
import org.example.aispingboot.enumClass.WorkOrderStatus;
import org.example.aispingboot.mapper.CrisisWorkOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 工单超时检查的消费者：延迟消息到期后判断是否需要升级。
 * <p>
 * <b>核心逻辑是「回查当前状态」而非「直接升级」</b>：
 * 消息投递出去后，工单可能已经被认领或闭环——此时消息才到期。
 * 因此消费者必须回数据库看最新状态，只有仍然「待认领」才执行升级。
 * 这也让整个处理天然幂等：重复消费同一条消息不会造成错误升级。
 */
@Component
public class CrisisTimeoutConsumer {

    private static final Logger log = LoggerFactory.getLogger(CrisisTimeoutConsumer.class);

    private final CrisisWorkOrderMapper crisisWorkOrderMapper;
    private final NotificationService notificationService;

    public CrisisTimeoutConsumer(CrisisWorkOrderMapper crisisWorkOrderMapper,
                                 NotificationService notificationService) {
        this.crisisWorkOrderMapper = crisisWorkOrderMapper;
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RabbitMQConfig.CRISIS_TIMEOUT_CHECK_QUEUE)
    public void onTimeout(CrisisTimeoutProducer.TimeoutMessage message,
                          Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        Long orderId = message == null ? null : message.getOrderId();
        if (orderId == null) {
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            CrisisWorkOrder order = crisisWorkOrderMapper.selectById(orderId);
            if (order == null) {
                // 工单已被删除（如学生注销）：无可升级，直接确认
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 已认领或已闭环 → 说明有人在时限内响应了，无需升级
            if (!WorkOrderStatus.PENDING.getCode().equals(order.getStatus())) {
                log.debug("工单 {} 已处于 {}，无需升级", orderId, order.getStatus());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 已经升级过就不再重复：避免同一条工单反复通知管理员
            if (Integer.valueOf(1).equals(order.getEscalated())) {
                channel.basicAck(deliveryTag, false);
                return;
            }

            escalate(order);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // 升级失败：拒绝且不重新入队，避免无限重投。
            // 工单仍在待认领列表中，管理员可人工介入。
            log.error("工单 {} 超时升级处理失败：{}", orderId, e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /**
     * 执行升级：标记工单并通知管理员。
     * <p>
     * 升级的含义是「把这件事上升到更高层级」——原本指望辅导员认领，
     * 现在已超时，需要管理员知晓并推动。
     */
    private void escalate(CrisisWorkOrder order) {
        order.setEscalated(1);
        order.setEscalatedAt(LocalDateTime.now());
        crisisWorkOrderMapper.updateById(order);

        String waited = describeWait(order.getCreatedAt());
        String title = String.format("【工单超时升级】工单 #%d 超过 %s无人认领",
                order.getId(), waited);
        String content = String.format(
                "该工单涉及学生（ID: %d），因超过认领时限仍未处理已自动升级。\n"
                        + "来源：%s\n"
                        + "请尽快安排人员跟进，或联系相关辅导员了解情况。",
                order.getUserId(), order.getSource());

        // 通知管理员：辅导员未响应时，需要更高层级介入推动
        notificationService.notifyRole(2, Notification.TYPE_ORDER_ESCALATED,
                title, content, order.getId());

        log.warn("工单 {} 超时 {}未认领，已升级并通知管理员", order.getId(), waited);
    }

    /**
     * 描述等待时长。
     * <p>
     * 不足一分钟时按秒展示：超时时间是可配置的，若配成几十秒，
     * 直接取分钟数会得到「超过 0 分钟无人认领」这种读起来像 bug 的文案。
     */
    private String describeWait(LocalDateTime createdAt) {
        if (createdAt == null) {
            return "限时";
        }
        Duration waited = Duration.between(createdAt, LocalDateTime.now());
        return waited.toMinutes() >= 1
                ? waited.toMinutes() + " 分钟"
                : Math.max(1, waited.toSeconds()) + " 秒";
    }
}
