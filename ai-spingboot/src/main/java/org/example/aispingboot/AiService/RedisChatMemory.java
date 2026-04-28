package org.example.aispingboot.AiService;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 分层会话记忆（基于 Redis 实现 ChatMemory 接口）。
 * <p>
 * <b>为什么需要分层</b>：单一滑动窗口存在一个明显缺陷——超出窗口的对话被直接丢弃。
 * 用户在第 5 轮说过「我妈妈最近生病了」，到第 40 轮时 AI 已完全不知情，
 * 可能再次追问，让用户觉得「我说过了你怎么还问」。
 * <p>
 * <b>两层结构</b>：
 * <ul>
 *   <li><b>长期层</b>：早期对话经 AI 压缩成的摘要（{@link ConversationSummaryService} 维护）</li>
 *   <li><b>短期层</b>：最近 {@value #MAX_MESSAGES} 条完整对话，保留细节与语气</li>
 * </ul>
 * 读取时把摘要作为一条系统消息置于最前，再接上短期窗口的原始消息，
 * 模型因此既知道早期要点，也能看到近期的完整上下文。
 * <p>
 * <b>滚动而非重建</b>：消息一旦被窗口裁掉，就异步并入长期摘要，而不是每轮重新摘要全部历史——
 * 后者会随对话变长而线性增加开销。
 * <p>
 * 数据结构：
 * <pre>
 *   chat:memory:{conversationId}   短期窗口（Redis List，{@value #MAX_MESSAGES} 条）
 *   chat:summary:{conversationId}  长期摘要（Redis String，由摘要服务维护）
 * </pre>
 */
@Component
public class RedisChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(RedisChatMemory.class);

    private static final String KEY_PREFIX = "chat:memory:";
    /** 滑动窗口大小：保留最近多少条完整消息 */
    private static final int MAX_MESSAGES = 30;
    /** key 过期时间，防止冷会话数据无限膨胀 */
    private static final Duration KEY_TTL = Duration.ofDays(7);

    /** 长期摘要在注入上下文时的前缀，帮助模型区分「这是较早内容的压缩」 */
    private static final String SUMMARY_PREFIX = "【此前对话的要点摘要】\n";

    private final StringRedisTemplate redisTemplate;
    private final ConversationSummaryService summaryService;

    public RedisChatMemory(StringRedisTemplate redisTemplate,
                           ConversationSummaryService summaryService) {
        this.redisTemplate = redisTemplate;
        this.summaryService = summaryService;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        if (!StringUtils.hasText(conversationId) || messages == null || messages.isEmpty()) {
            return;
        }
        String key = KEY_PREFIX + conversationId;
        for (Message message : messages) {
            redisTemplate.opsForList().rightPush(key, serialize(message));
        }

        // 超出窗口的部分不直接丢弃，而是并入长期摘要
        rollOverflowIntoSummary(conversationId, key);

        redisTemplate.expire(key, KEY_TTL);
    }

    /**
     * 把超出窗口的旧消息滚动进长期摘要。
     * <p>
     * 先取出待裁掉的部分再 trim：若先 trim 再取，内容已经丢失。
     * 摘要动作本身是异步的（见 {@link ConversationSummaryService#rollUp}），
     * 因此不会拖慢记忆写入这条对话主链路。
     */
    private void rollOverflowIntoSummary(String conversationId, String key) {
        try {
            Long size = redisTemplate.opsForList().size(key);
            if (size == null || size <= MAX_MESSAGES) {
                return;
            }
            long overflow = size - MAX_MESSAGES;

            // 取出将被裁掉的消息（索引 0 到 overflow-1，即最早的那几条）
            List<String> toRollUp = redisTemplate.opsForList().range(key, 0, overflow - 1);
            redisTemplate.opsForList().trim(key, -MAX_MESSAGES, -1);

            if (toRollUp == null || toRollUp.isEmpty()) {
                return;
            }
            List<Message> overflowMessages = new ArrayList<>(toRollUp.size());
            for (String item : toRollUp) {
                Message message = deserialize(item);
                if (message != null) {
                    overflowMessages.add(message);
                }
            }
            if (!overflowMessages.isEmpty()) {
                summaryService.rollUp(conversationId, overflowMessages);
            }
        } catch (Exception e) {
            // 摘要失败不应中断记忆写入：最坏情况是这段内容没有被长期记住
            log.warn("会话 {} 溢出内容并入摘要失败：{}", conversationId, e.getMessage());
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        List<Message> result = new ArrayList<>();

        // 长期层：摘要在最前，作为背景知识注入
        String summary = summaryService.getSummary(conversationId);
        if (StringUtils.hasText(summary)) {
            result.add(new SystemMessage(SUMMARY_PREFIX + summary));
        }

        // 短期层：原始消息，保留语气与细节
        String key = KEY_PREFIX + conversationId;
        List<String> items = redisTemplate.opsForList().range(key, 0, -1);
        if (items != null) {
            for (String item : items) {
                Message message = deserialize(item);
                if (message != null) {
                    result.add(message);
                }
            }
        }
        return result;
    }

    @Override
    public void clear(String conversationId) {
        redisTemplate.delete(KEY_PREFIX + conversationId);
        // 长期摘要一并清除，否则会出现「对话记录没了、但摘要还在」的残留
        summaryService.clear(conversationId);
    }

    private String serialize(Message message) {
        JSONObject obj = new JSONObject();
        MessageType type = message.getMessageType();
        obj.set("type", type == null ? "USER" : type.name());
        obj.set("content", message.getText());
        return obj.toString();
    }

    private Message deserialize(String item) {
        try {
            JSONObject obj = JSONUtil.parseObj(item);
            String type = obj.getStr("type");
            String content = obj.getStr("content", "");
            return switch (type) {
                case "USER" -> new UserMessage(content);
                case "ASSISTANT" -> new AssistantMessage(content);
                case "SYSTEM" -> new SystemMessage(content);
                default -> new UserMessage(content);
            };
        } catch (Exception e) {
            log.warn("记忆反序列化失败，已跳过该条：{}", e.getMessage());
            return null;
        }
    }
}
