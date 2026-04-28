package org.example.aispingboot.AiService.context;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent 调用上下文。
 * <p>
 * <b>它解决什么问题</b>：工具方法只知道模型传进来的参数，不知道「当前是谁在对话、属于哪个会话」。
 * 但很多工具需要这些信息——例如「查询我的情绪历史」必须知道是哪个学生。
 * <p>
 * <b>为什么不用 ThreadLocal</b>：工具执行发生在 Reactor 的弹性线程池上
 * （实测为 {@code boundedElastic-N}），与发起请求的 Tomcat 线程不是同一个，
 * ThreadLocal 取不到值。Spring AI 提供的 {@link ToolContext} 是随调用链传递的，
 * 且对模型不可见（不参与工具签名），因此是唯一可靠的选择。
 * <p>
 * 使用方式：在 {@code ChatClient} 调用处通过 {@code .toolContext(AgentContext.of(...))} 注入，
 * 工具方法声明一个 {@link ToolContext} 参数即可读取。
 */
public final class AgentContext {

    /** 轮次标识：一次用户发言对应一个轮次 */
    public static final String TURN_ID = "agentTurnId";
    /** 会话ID */
    public static final String SESSION_ID = "agentSessionId";
    /** 当前用户（学生）ID */
    public static final String USER_ID = "agentUserId";
    /** 轮次内的步骤计数器，用于轨迹排序 */
    public static final String STEP_COUNTER = "agentStepCounter";

    private AgentContext() {
    }

    /**
     * 构建一次调用的上下文。
     * <p>
     * 步骤计数器随上下文创建，使每个轮次拥有独立计数——
     * 无需共享状态，也不存在多实例部署下的计数冲突。
     */
    public static Map<String, Object> of(String turnId, Long sessionId, Long userId) {
        Map<String, Object> context = new HashMap<>();
        context.put(TURN_ID, turnId);
        context.put(SESSION_ID, sessionId);
        context.put(USER_ID, userId);
        context.put(STEP_COUNTER, new AtomicInteger(0));
        return context;
    }

    /** 从工具上下文取出指定键（工具方法内部使用） */
    public static Object get(ToolContext toolContext, String key) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        return toolContext.getContext().get(key);
    }

    /** 从工具上下文取出 Long 值 */
    public static Long getLong(ToolContext toolContext, String key) {
        return toLong(get(toolContext, key));
    }

    /** 从工具上下文取出字符串值 */
    public static String getString(ToolContext toolContext, String key) {
        Object value = get(toolContext, key);
        return value == null ? null : String.valueOf(value);
    }

    private static Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return value == null ? null : Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}
