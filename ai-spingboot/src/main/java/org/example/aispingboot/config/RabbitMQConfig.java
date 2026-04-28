package org.example.aispingboot.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置。
 * <p>
 * <b>为什么引入消息队列</b>：文章向量化需要调用 Embedding 接口，耗时且会失败
 * （实测在批量导入时曾因限流触发 Connection reset，导致文章漏建索引）。
 * 原先同步执行会让管理员「点发布」一直等到向量化结束；改成消息队列后：
 * <ul>
 *   <li><b>解耦</b>：发布接口立即返回，向量化在后台进行</li>
 *   <li><b>削峰</b>：消费者串行处理，天然避免密集调用触发服务端限流</li>
 *   <li><b>重试</b>：失败自动重试，超过次数进入死信队列，不再静默丢失</li>
 * </ul>
 * <p>
 * 队列结构（含死信队列）：
 * <pre>
 *   knowledge.index.exchange --article.index--> knowledge.index.queue
 *                                                      | 重试耗尽
 *                                                      v
 *                        knowledge.index.dlx --article.index.dlq--> knowledge.index.dlq
 * </pre>
 * 死信队列的存在很关键：没有它，重试失败的消息会被直接丢弃，
 * 那就退化成了「静默失败」——和引入 MQ 之前的老问题一样。
 */
@Configuration
public class RabbitMQConfig {

    /**
     * 高优先级工单的认领时限（秒）。
     * <p>
     * 通过配置注入而非写死：不同学校的响应能力不同，
     * 且测试时需要把它调小以便验证升级链路。
     */
    @Value("${crisis.timeout.high-seconds:900}")
    private long highTimeoutSeconds;

    /** 一般工单的认领时限（秒） */
    @Value("${crisis.timeout.normal-seconds:3600}")
    private long normalTimeoutSeconds;

    /** 业务交换机 */
    public static final String INDEX_EXCHANGE = "knowledge.index.exchange";
    /** 业务队列 */
    public static final String INDEX_QUEUE = "knowledge.index.queue";
    /** 业务路由键 */
    public static final String INDEX_ROUTING_KEY = "article.index";

    // ==================== 危机工单超时升级（延迟队列） ====================
    // 实现方式：TTL + 死信交换机。
    // 消息投递到「无消费者的延迟队列」，TTL 到期后变成死信，经 DLX 路由到检查队列被消费。

    /** 延迟消息交换机 */
    public static final String CRISIS_TIMEOUT_EXCHANGE = "crisis.timeout.exchange";

    /**
     * 高优先级工单的延迟队列。
     * <p>
     * <b>为什么要按紧急程度拆成两个队列</b>：RabbitMQ 只从队首过期消息——
     * 若把 10 分钟和 60 分钟的工单混在同一队列，排在队首的长 TTL 消息会
     * 阻塞后面短 TTL 消息的过期，导致高优先级工单的实际升级时间被严重推迟。
     * 每个 TTL 一个队列是官方推荐的解法。
     */
    public static final String CRISIS_TIMEOUT_HIGH_QUEUE = "crisis.timeout.high";
    public static final String CRISIS_TIMEOUT_HIGH_KEY = "crisis.timeout.high.key";

    /** 一般工单的延迟队列 */
    public static final String CRISIS_TIMEOUT_NORMAL_QUEUE = "crisis.timeout.normal";
    public static final String CRISIS_TIMEOUT_NORMAL_KEY = "crisis.timeout.normal.key";

    /** 超时检查队列的交换机 */
    public static final String CRISIS_TIMEOUT_DLX = "crisis.timeout.dlx";
    /** 超时检查队列（真正被消费者订阅） */
    public static final String CRISIS_TIMEOUT_CHECK_QUEUE = "crisis.timeout.check";
    public static final String CRISIS_TIMEOUT_CHECK_KEY = "crisis.timeout.check.key";

    /** 死信交换机 */
    public static final String INDEX_DLX = "knowledge.index.dlx";
    /** 死信队列：人工排查与后续补偿的入口 */
    public static final String INDEX_DLQ = "knowledge.index.dlq";
    /** 死信路由键 */
    public static final String INDEX_DLQ_ROUTING_KEY = "article.index.dlq";

    /**
     * JSON 消息转换器。
     * <p>
     * 默认的 SimpleMessageConverter 用 Java 序列化，消息体不可读、且强依赖类的全限定名，
     * 换包名或改类结构就会反序列化失败。JSON 格式人可读、语言无关，也便于在
     * RabbitMQ 管理台直接查看消息内容排查问题。
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    @Bean
    public DirectExchange indexExchange() {
        return new DirectExchange(INDEX_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange indexDlx() {
        return new DirectExchange(INDEX_DLX, true, false);
    }

    /**
     * 业务队列，绑定死信交换机。
     * <p>
     * 消息被拒绝且不重新入队（或重试次数耗尽）时，会自动路由到死信队列，
     * 从而保证「失败的消息一定有归宿」，不会被无声丢弃。
     */
    @Bean
    public Queue indexQueue() {
        return QueueBuilder.durable(INDEX_QUEUE)
                .deadLetterExchange(INDEX_DLX)
                .deadLetterRoutingKey(INDEX_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue indexDlq() {
        return QueueBuilder.durable(INDEX_DLQ).build();
    }

    @Bean
    public Binding indexBinding() {
        return BindingBuilder.bind(indexQueue()).to(indexExchange()).with(INDEX_ROUTING_KEY);
    }

    @Bean
    public Binding indexDlqBinding() {
        return BindingBuilder.bind(indexDlq()).to(indexDlx()).with(INDEX_DLQ_ROUTING_KEY);
    }

    // ==================== 危机工单超时升级 ====================

    @Bean
    public DirectExchange crisisTimeoutExchange() {
        return new DirectExchange(CRISIS_TIMEOUT_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange crisisTimeoutDlx() {
        return new DirectExchange(CRISIS_TIMEOUT_DLX, true, false);
    }

    /**
     * 高优先级延迟队列：不设消费者，消息只在这里等待 TTL 到期。
     * <p>
     * 队列级别的 TTL 通过 {@code x-message-ttl} 设置——比逐条设置更简单，
     * 也避免了「队首阻塞」问题（同队列所有消息 TTL 一致）。
     */
    @Bean
    public Queue crisisTimeoutHighQueue() {
        return QueueBuilder.durable(CRISIS_TIMEOUT_HIGH_QUEUE)
                .ttl(toTtlMillis(highTimeoutSeconds))
                .deadLetterExchange(CRISIS_TIMEOUT_DLX)
                .deadLetterRoutingKey(CRISIS_TIMEOUT_CHECK_KEY)
                .build();
    }

    @Bean
    public Queue crisisTimeoutNormalQueue() {
        return QueueBuilder.durable(CRISIS_TIMEOUT_NORMAL_QUEUE)
                .ttl(toTtlMillis(normalTimeoutSeconds))
                .deadLetterExchange(CRISIS_TIMEOUT_DLX)
                .deadLetterRoutingKey(CRISIS_TIMEOUT_CHECK_KEY)
                .build();
    }

    /**
     * 秒 → 毫秒，并钳制到 int 范围。
     * <p>
     * QueueBuilder.ttl 只接受 int，而配置项是 long。
     * 这里做上界保护：若配置了一个离谱的大值（如误填天数），
     * 溢出会变成负数而导致消息立即过期——那会让所有工单被瞬间升级。
     */
    private int toTtlMillis(long seconds) {
        long millis = Math.max(1, seconds) * 1000L;
        return (int) Math.min(millis, Integer.MAX_VALUE);
    }

    /**
     * 超时检查队列：由消费者订阅，收到消息时说明对应的工单已超过认领时限。
     * <p>
     * 注意这里不设 TTL——它需要立即被消费，而不是继续等待。
     */
    @Bean
    public Queue crisisTimeoutCheckQueue() {
        return QueueBuilder.durable(CRISIS_TIMEOUT_CHECK_QUEUE).build();
    }

    @Bean
    public Binding crisisTimeoutHighBinding() {
        return BindingBuilder.bind(crisisTimeoutHighQueue())
                .to(crisisTimeoutExchange()).with(CRISIS_TIMEOUT_HIGH_KEY);
    }

    @Bean
    public Binding crisisTimeoutNormalBinding() {
        return BindingBuilder.bind(crisisTimeoutNormalQueue())
                .to(crisisTimeoutExchange()).with(CRISIS_TIMEOUT_NORMAL_KEY);
    }

    @Bean
    public Binding crisisTimeoutCheckBinding() {
        return BindingBuilder.bind(crisisTimeoutCheckQueue())
                .to(crisisTimeoutDlx()).with(CRISIS_TIMEOUT_CHECK_KEY);
    }
}
