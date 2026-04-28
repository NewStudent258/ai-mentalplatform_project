package org.example.aispingboot.AiService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

/**
 * 对话摘要服务：分层记忆的「长期层」。
 * <p>
 * <b>解决什么问题</b>：会话记忆原先只有一个 30 条的滑动窗口，超出窗口的对话会被直接丢弃。
 * 这意味着长对话里「用户最早提到过什么」会彻底消失——用户在第 5 轮说过
 * 「我妈妈最近生病了」，到了第 40 轮 AI 已经完全不知道这件事，
 * 于是可能再次追问，让用户觉得「我说过了你怎么还问」。
 * <p>
 * <b>分层结构</b>：
 * <pre>
 *   长期层：早期对话的 AI 摘要（Redis String，跨窗口保留）
 *   短期层：最近 N 条完整对话（Redis List，保证细节与语气）
 * </pre>
 * 读取时把摘要作为一条系统消息放在最前，再接上短期窗口的原始消息，
 * 模型因此既能记住要点，也能看到近期的完整上下文。
 * <p>
 * <b>滚动更新</b>：摘要不是每次重新生成，而是「旧摘要 + 新溢出内容」滚动合并，
 * 这样既避免重复消耗，也让早期信息经过多轮压缩后仍然留存。
 */
@Service
public class ConversationSummaryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationSummaryService.class);

    /** 长期摘要在 Redis 中的键前缀 */
    public static final String SUMMARY_KEY_PREFIX = "chat:summary:";

    /** 摘要长度上限：超出则截断，防止长期累积挤占上下文 */
    private static final int MAX_SUMMARY_LENGTH = 800;

    /** 摘要有效期与短期记忆保持一致，避免出现「记忆还在但摘要过期」的割裂 */
    private static final Duration KEY_TTL = Duration.ofDays(7);

    private final ChatClient conversationSummaryChatClient;
    private final StringRedisTemplate redisTemplate;

    public ConversationSummaryService(
            @Qualifier("conversation-summary") ChatClient conversationSummaryChatClient,
            StringRedisTemplate redisTemplate) {
        this.conversationSummaryChatClient = conversationSummaryChatClient;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 把溢出的对话并入长期摘要。
     * <p>
     * <b>异步执行</b>：摘要需要调用大模型，若同步执行会让每条消息的写入都变慢，
     * 而记忆写入发生在对话主链路上，绝不能因此拖慢响应。
     * <p>
     * <b>失败降级</b>：任何异常都只记录日志，保留原有摘要不变。
     * 最坏情况是「这段溢出内容没有被摘要进长期记忆」——
     * 相比让异常穿透到对话流程，这是可接受的降级。
     *
     * @param conversationId 会话标识
     * @param overflow       被窗口裁掉的消息
     */
    @Async("aiAnalysisExecutor")
    public void rollUp(String conversationId, List<Message> overflow) {
        if (conversationId == null || overflow == null || overflow.isEmpty()) {
            return;
        }
        try {
            String previous = getSummary(conversationId);
            String merged = summarize(previous, overflow);
            if (!StringUtils.hasText(merged)) {
                return;
            }
            redisTemplate.opsForValue().set(SUMMARY_KEY_PREFIX + conversationId, merged, KEY_TTL);
            log.info("会话 {} 长期摘要已更新：并入 {} 条消息，当前摘要 {} 字",
                    conversationId, overflow.size(), merged.length());
        } catch (Exception e) {
            // 保留旧摘要不动：宁可少记一点，也不能因为摘要失败而把已有记忆清空
            log.warn("会话 {} 摘要更新失败，保留原有摘要：{}", conversationId, e.getMessage());
        }
    }

    /** 读取长期摘要，无则返回 null */
    public String getSummary(String conversationId) {
        if (conversationId == null) {
            return null;
        }
        try {
            return redisTemplate.opsForValue().get(SUMMARY_KEY_PREFIX + conversationId);
        } catch (Exception e) {
            log.warn("读取会话 {} 摘要失败：{}", conversationId, e.getMessage());
            return null;
        }
    }

    /** 清除摘要（会话删除时调用） */
    public void clear(String conversationId) {
        if (conversationId == null) {
            return;
        }
        try {
            redisTemplate.delete(SUMMARY_KEY_PREFIX + conversationId);
        } catch (Exception e) {
            log.warn("清除会话 {} 摘要失败：{}", conversationId, e.getMessage());
        }
    }

    /**
     * 调用模型压缩「旧摘要 + 新溢出内容」。
     * <p>
     * 注意每次都是「旧摘要 + 新增内容」再压缩一次，而不是把所有历史重新摘要：
     * 前者消耗恒定，后者会随对话变长而线性增长。
     */
    private String summarize(String previousSummary, List<Message> overflow) {
        StringBuilder input = new StringBuilder();
        if (StringUtils.hasText(previousSummary)) {
            input.append("【此前的对话摘要】\n").append(previousSummary).append("\n\n");
        }
        input.append("【需要并入摘要的新对话】\n");
        for (Message message : overflow) {
            String role = switch (message.getMessageType()) {
                case USER -> "用户";
                case ASSISTANT -> "AI助手";
                case SYSTEM -> "系统";
                default -> "其他";
            };
            String text = message.getText();
            if (StringUtils.hasText(text)) {
                input.append(role).append("：").append(text).append("\n");
            }
        }
        input.append("\n请把上述内容合并成一份新的摘要。");

        String result = conversationSummaryChatClient.prompt()
                .user(input.toString())
                .call()
                .content();

        if (!StringUtils.hasText(result)) {
            return previousSummary;
        }

        String trimmed = result.trim();
        if (trimmed.length() > MAX_SUMMARY_LENGTH) {
            // 超长时截断而非丢弃：截断只是丢失尾部细节，丢弃会让整段记忆消失
            log.debug("摘要超长（{} 字），已截断至 {} 字", trimmed.length(), MAX_SUMMARY_LENGTH);
            trimmed = trimmed.substring(0, MAX_SUMMARY_LENGTH) + "…";
        }
        return trimmed;
    }
}
