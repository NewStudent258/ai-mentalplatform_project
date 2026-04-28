package org.example.aispingboot.AiService.mq;

import com.rabbitmq.client.Channel;
import org.example.aispingboot.AiService.KnowledgeIndexService;
import org.example.aispingboot.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 索引变更消息消费者。
 * <p>
 * 采用手动 ACK，配合「重试 + 死信」保证消息不丢：
 * <ul>
 *   <li><b>成功</b>：ACK 确认</li>
 *   <li><b>可重试的失败</b>（如 Embedding 限流、网络抖动）：手动重投一条
 *       携带自增重试计数的新消息，再 ACK 原消息</li>
 *   <li><b>重试耗尽</b>：NACK 且不重新入队，消息经死信交换机进入死信队列，
 *       保留现场供人工排查</li>
 * </ul>
 * <p>
 * <b>为什么不直接用 NACK + requeue 做重试</b>：
 * <ol>
 *   <li>重新入队时消息头<b>不会变化</b>，无法记录已重试次数，
 *       一旦遇到必然失败的消息（毒消息）就会无限重投，持续占用消费者；</li>
 *   <li>requeue 的消息回到队首，会被立即再次消费，形成紧密的失败循环，
 *       没有退避时间，对已经限流的 Embedding 接口是雪上加霜。</li>
 * </ol>
 * 手动重投则把消息放回队尾，且能携带计数，行为可控可观测。
 */
@Component
public class IndexMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(IndexMessageConsumer.class);

    /** 单条消息的最大处理次数（含首次） */
    private static final int MAX_ATTEMPT = 3;
    /** 重试计数在消息头中的键名 */
    private static final String RETRY_HEADER = "x-retry-count";

    private final KnowledgeIndexService knowledgeIndexService;
    private final RabbitTemplate rabbitTemplate;

    public IndexMessageConsumer(KnowledgeIndexService knowledgeIndexService,
                                RabbitTemplate rabbitTemplate) {
        this.knowledgeIndexService = knowledgeIndexService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = RabbitMQConfig.INDEX_QUEUE)
    public void onMessage(IndexMessage message,
                          Message rawMessage,
                          Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                          @Header(name = RETRY_HEADER, required = false) Integer retryCount) throws IOException {
        Long articleId = message.getArticleId();
        int attempt = (retryCount == null ? 0 : retryCount) + 1;

        try {
            // 消费者主动回查数据库最新状态：这样即使同一篇文章的多条消息乱序到达，
            // 最终结果也一定与数据库一致（幂等）
            boolean success = IndexMessage.OP_REMOVE.equals(message.getOperation())
                    ? knowledgeIndexService.removeArticle(articleId)
                    : knowledgeIndexService.syncArticle(articleId);

            // 关键：服务层用返回值表达成功与否，而不是抛异常。
            // 若这里不检查返回值，失败会被当成成功 ACK 掉，消息直接消失——
            // 那重试和死信队列就形同虚设。
            if (!success) {
                throw new IllegalStateException(
                        "索引处理未成功：文章 " + articleId + " 操作 " + message.getOperation());
            }

            channel.basicAck(deliveryTag, false);
            log.debug("索引消息处理成功：文章 {} 操作 {}", articleId, message.getOperation());

        } catch (Exception e) {
            if (attempt < MAX_ATTEMPT) {
                requeueWithIncrementedRetry(message, retryCount, attempt);
                channel.basicAck(deliveryTag, false);
                log.warn("索引消息处理失败（第 {}/{} 次），已重新入队等待重试：文章 {}，原因：{}",
                        attempt, MAX_ATTEMPT, articleId, e.getMessage());
            } else {
                // 重试耗尽：拒绝且不重新入队，消息经死信交换机进入死信队列保留现场。
                // 这里绝不能直接丢弃——静默失败正是引入 MQ 之前的老问题
                channel.basicNack(deliveryTag, false, false);
                log.error("索引消息重试 {} 次仍失败，转入死信队列：文章 {}，原因：{}",
                        MAX_ATTEMPT, articleId, e.getMessage());
            }
        }
    }

    /**
     * 重投一条重试计数 +1 的消息到业务队列（进入队尾）。
     * <p>
     * 保留原始消息体与属性，只更新重试计数头，因此消息内容不会丢失或被篡改。
     */
    private void requeueWithIncrementedRetry(IndexMessage payload, Integer retryCount, int nextAttempt) {
        Message retryMessage = MessageBuilder.withBody(serialize(payload))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setHeader(RETRY_HEADER, nextAttempt)
                .build();
        rabbitTemplate.send(RabbitMQConfig.INDEX_EXCHANGE, RabbitMQConfig.INDEX_ROUTING_KEY, retryMessage);
    }

    /** 复用模板的消息转换器序列化，保证与原消息格式一致 */
    private byte[] serialize(IndexMessage payload) {
        return ((org.springframework.amqp.support.converter.Jackson2JsonMessageConverter)
                rabbitTemplate.getMessageConverter()).toMessage(payload, new MessageProperties()).getBody();
    }
}
