package org.example.aispingboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.aispingboot.AiService.context.AgentContext;
import org.example.aispingboot.entity.AgentTrace;
import org.example.aispingboot.mapper.AgentTraceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent 执行轨迹记录。
 * <p>
 * <b>核心原则：轨迹记录绝不能影响主链路。</b>
 * 它属于可观测性能力，记录失败最多是「少了一条轨迹」，
 * 若因为写库异常导致用户对话中断，那就本末倒置了。因此本类所有写入方法都吞掉异常。
 * <p>
 * <b>关于「思考步骤」</b>：真实 Agent 的 ReAct 轨迹包含显式的 Thought 文本。
 * 本项目的对话模型并未被要求输出思考过程（强制输出会污染用户可见的回复），
 * 因此这里不伪造「思考」步骤，只记录客观发生的事实：
 * 轮次开始、工具调用（含入参与结果）、内容生成。
 * 这三者已经构成完整的 Action → Observation → Response 链路，足以支撑问题定位。
 */
@Service
public class AgentTraceService {

    private static final Logger log = LoggerFactory.getLogger(AgentTraceService.class);

    /** ToolContext 中承载轮次标识的键（统一定义在 AgentContext，避免多处维护） */
    public static final String CTX_TURN_ID = AgentContext.TURN_ID;
    /** ToolContext 中承载会话ID的键 */
    public static final String CTX_SESSION_ID = AgentContext.SESSION_ID;
    /** ToolContext 中承载步骤计数器的键（每轮独立，避免共享状态） */
    public static final String CTX_STEP_COUNTER = AgentContext.STEP_COUNTER;

    /** 结果摘要的最大长度，避免超长工具输出把轨迹表撑大 */
    private static final int MAX_SUMMARY_LENGTH = 480;
    /** 入参的最大长度 */
    private static final int MAX_INPUT_LENGTH = 480;

    private final AgentTraceMapper agentTraceMapper;

    public AgentTraceService(AgentTraceMapper agentTraceMapper) {
        this.agentTraceMapper = agentTraceMapper;
    }

    /**
     * 开启一个新轮次。
     * <p>
     * 步骤计数器随轮次创建并放入 ToolContext：这样每个轮次有独立的计数，
     * 无需共享状态、也不存在多实例下的计数冲突。
     *
     * @return 本轮次的上下文 Map，调用方需通过 {@code .toolContext(...)} 传给 ChatClient
     */
    public Map<String, Object> startTurn(Long sessionId, Long userId, String userMessage) {
        // 轮次标识用「会话ID_时间戳」而非 UUID：可读性更好，排查时能直接看出属于哪个会话
        String turnId = sessionId + "_" + System.currentTimeMillis();

        record(sessionId, userId, turnId, 0, AgentTrace.TYPE_TURN_START,
                null, null, userMessage, null);

        // 上下文由 AgentContext 统一构建：工具方法也从同一份上下文里读取用户身份，
        // 因此这里必须带上 userId，否则「查询我的情绪历史」这类工具无从知道查谁
        Map<String, Object> context = AgentContext.of(turnId, sessionId, userId);
        // 轮次开始已占用步骤 0，计数器从 1 开始
        context.put(CTX_STEP_COUNTER, new AtomicInteger(1));
        return context;
    }

    /**
     * 记录一次工具调用。
     * <p>
     * 由 AOP 切面在 @Tool 方法执行前后自动调用，业务代码无需感知。
     */
    public void recordToolCall(Map<String, Object> context, String toolName,
                               String input, String result, long durationMs) {
        try {
            Long sessionId = asLong(context.get(CTX_SESSION_ID));
            String turnId = String.valueOf(context.get(CTX_TURN_ID));
            int step = nextStep(context);

            record(sessionId, null, turnId, step, AgentTrace.TYPE_TOOL_CALL,
                    toolName, input, result, durationMs);
        } catch (Exception e) {
            log.warn("记录工具调用轨迹失败（不影响主流程）：{}", e.getMessage());
        }
    }

    /** 记录内容生成完成 */
    public void recordGeneration(Map<String, Object> context, String summary, long durationMs) {
        try {
            Long sessionId = asLong(context.get(CTX_SESSION_ID));
            String turnId = String.valueOf(context.get(CTX_TURN_ID));
            int step = nextStep(context);

            record(sessionId, null, turnId, step, AgentTrace.TYPE_GENERATION,
                    null, null, summary, durationMs);
        } catch (Exception e) {
            log.warn("记录生成轨迹失败（不影响主流程）：{}", e.getMessage());
        }
    }

    /** 记录安全拦截（受限话题未交由模型处理） */
    public void recordSafetyBlock(Map<String, Object> context, String category, String reason) {
        try {
            Long sessionId = asLong(context.get(CTX_SESSION_ID));
            String turnId = String.valueOf(context.get(CTX_TURN_ID));
            int step = nextStep(context);

            record(sessionId, null, turnId, step, AgentTrace.TYPE_SAFETY_BLOCK,
                    category, null, reason, null);
        } catch (Exception e) {
            log.warn("记录安全拦截轨迹失败（不影响主流程）：{}", e.getMessage());
        }
    }

    /** 查询某个会话的全部轨迹（按时间正序，便于前端按轮次还原执行过程） */
    public List<AgentTrace> listBySession(Long sessionId) {
        LambdaQueryWrapper<AgentTrace> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTrace::getSessionId, sessionId)
                .orderByAsc(AgentTrace::getCreatedAt)
                .orderByAsc(AgentTrace::getId);
        return agentTraceMapper.selectList(wrapper);
    }

    /** 从 ToolContext 取下一个步骤序号 */
    private int nextStep(Map<String, Object> context) {
        Object counter = context.get(CTX_STEP_COUNTER);
        if (counter instanceof AtomicInteger atomic) {
            return atomic.getAndIncrement();
        }
        return 0;
    }

    /** 统一的落库入口，负责字段截断 */
    private void record(Long sessionId, Long userId, String turnId, int stepIndex,
                        String stepType, String toolName, String toolInput,
                        String resultSummary, Long durationMs) {
        try {
            AgentTrace trace = AgentTrace.builder()
                    .turnId(turnId)
                    .sessionId(sessionId)
                    .userId(userId)
                    .stepIndex(stepIndex)
                    .stepType(stepType)
                    .toolName(toolName)
                    .toolInput(truncate(toolInput, MAX_INPUT_LENGTH))
                    .resultSummary(truncate(resultSummary, MAX_SUMMARY_LENGTH))
                    .durationMs(durationMs)
                    .createdAt(LocalDateTime.now())
                    .build();
            agentTraceMapper.insert(trace);
        } catch (Exception e) {
            log.warn("轨迹落库失败（不影响主流程）：{}", e.getMessage());
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private Long asLong(Object value) {
        if (value instanceof Long l) {
            return l;
        }
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
