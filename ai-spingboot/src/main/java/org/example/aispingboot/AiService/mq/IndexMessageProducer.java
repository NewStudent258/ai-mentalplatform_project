package org.example.aispingboot.AiService.mq;

import org.example.aispingboot.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 索引变更消息生产者。
 * <p>
 * 发送失败时只告警、不抛异常：向量索引是「派生数据」，
 * 它的一致性不该反过来阻断文章发布这条主业务链路。
 * 索引的最终一致由启动时的全量对账兜底（见 KnowledgeIndexService）。
 */
@Component
public class IndexMessageProducer {

    private static final Logger log = LoggerFactory.getLogger(IndexMessageProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public IndexMessageProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /** 通知消费者建立或刷新该文章的向量索引 */
    public void sendIndex(Long articleId) {
        send(IndexMessage.index(articleId));
    }

    /** 通知消费者移除该文章的向量索引 */
    public void sendRemove(Long articleId) {
        send(IndexMessage.remove(articleId));
    }

    private void send(IndexMessage message) {
        if (message.getArticleId() == null) {
            return;
        }
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.INDEX_EXCHANGE,
                    RabbitMQConfig.INDEX_ROUTING_KEY,
                    message);
            log.debug("已投递索引消息：文章 {} 操作 {}", message.getArticleId(), message.getOperation());
        } catch (Exception e) {
            // 消息投递失败不能影响主流程：文章照常发布，
            // 缺失的索引会在下次启动对账时被补上
            log.warn("索引消息投递失败：文章 {} 操作 {}，原因：{}。"
                            + "该文章的索引将在下次启动对账时补建",
                    message.getArticleId(), message.getOperation(), e.getMessage());
        }
    }
}
