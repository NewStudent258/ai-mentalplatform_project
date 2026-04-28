package org.example.aispingboot.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.aispingboot.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * 工单超时消息的生产者。
 * <p>
 * 工单创建后投递一条延迟消息，若在时限内无人认领，消息到期后会经死信交换机
 * 进入检查队列，由 {@link CrisisTimeoutConsumer} 执行升级。
 * <p>
 * <b>为什么用延迟队列而不是定时任务轮询</b>：
 * 轮询需要扫全表并判断「哪些工单超时了」，随工单量增长开销线性上升，
 * 且有精度损失（扫描间隔）。延迟消息把「什么时候该检查」交给消息中间件，
 * 到期即触发，精度高且无无效扫描。
 */
@Component
public class CrisisTimeoutProducer {

    private static final Logger log = LoggerFactory.getLogger(CrisisTimeoutProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public CrisisTimeoutProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 为工单投递一条超时检查消息。
     * <p>
     * 按紧急程度选择不同的延迟队列——两个队列的 TTL 不同。
     * 投递失败只记录日志：工单本身已经创建成功，超时升级属于增强能力，
     * 不应因为它失败而影响建单。
     */
    public void scheduleTimeoutCheck(Long orderId, Integer urgency) {
        if (orderId == null) {
            return;
        }
        boolean highPriority = urgency != null && urgency == 1;
        String routingKey = highPriority
                ? RabbitMQConfig.CRISIS_TIMEOUT_HIGH_KEY
                : RabbitMQConfig.CRISIS_TIMEOUT_NORMAL_KEY;
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.CRISIS_TIMEOUT_EXCHANGE, routingKey,
                    new TimeoutMessage(orderId));
            log.debug("已为工单 {} 投递超时检查消息（{}）", orderId, highPriority ? "高优先级" : "一般");
        } catch (Exception e) {
            log.warn("工单 {} 超时检查消息投递失败，该工单不会自动升级：{}", orderId, e.getMessage());
        }
    }

    /** 超时检查消息体：只带工单ID，消费者回查最新状态 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeoutMessage implements Serializable {
        private Long orderId;
    }
}
