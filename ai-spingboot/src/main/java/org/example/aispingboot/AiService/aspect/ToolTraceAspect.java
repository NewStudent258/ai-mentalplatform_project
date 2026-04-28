package org.example.aispingboot.AiService.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.example.aispingboot.service.AgentTraceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/**
 * 工具调用轨迹切面。
 * <p>
 * <b>为什么用 AOP 而不是在每个工具里写埋点</b>：
 * 埋点属于横切关注点，与工具本身的业务逻辑无关。若写进每个 @Tool 方法，
 * 一是重复代码散落各处，二是将来新增工具时容易漏掉，导致轨迹不完整——
 * 而轨迹一旦不完整，就失去了排查问题的价值。
 * <p>
 * <b>如何拿到上下文</b>：轮次信息通过 Spring AI 的 {@link ToolContext} 传递
 * （在 ChatClient 调用处通过 {@code .toolContext(...)} 注入）。切面从方法参数中取出它，
 * 因此工具方法只需声明该参数即可，无需关心埋点本身。
 * <p>
 * <b>异常处理</b>：埋点失败不得影响工具本身的返回结果，因此记录动作全部吞掉异常。
 */
@Aspect
@Component
public class ToolTraceAspect {

    private static final Logger log = LoggerFactory.getLogger(ToolTraceAspect.class);

    private final AgentTraceService agentTraceService;

    public ToolTraceAspect(AgentTraceService agentTraceService) {
        this.agentTraceService = agentTraceService;
    }

    /**
     * 拦截所有标注了 {@link Tool} 的方法。
     * <p>
     * 用注解切点而非包路径：包路径会随着代码结构调整失效，
     * 而「所有 @Tool 方法」才是真正想要表达的语义。
     */
    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object traceToolCall(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        String toolName = resolveToolName(joinPoint);

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable e) {
            // 工具抛异常也要留下轨迹：排查时「调了这个工具且它失败了」是关键信息
            long elapsed = System.currentTimeMillis() - start;
            findToolContext(joinPoint)
                    .ifPresent(ctx -> agentTraceService.recordToolCall(
                            ctx.getContext(), toolName, describeArgs(joinPoint), "工具执行异常：" + e.getMessage(), elapsed));
            throw e;
        }

        long elapsed = System.currentTimeMillis() - start;
        findToolContext(joinPoint).ifPresent(ctx ->
                agentTraceService.recordToolCall(
                        ctx.getContext(), toolName, describeArgs(joinPoint), String.valueOf(result), elapsed));

        return result;
    }

    /** 取工具名：优先用注解上声明的 name，与模型看到的一致 */
    private String resolveToolName(ProceedingJoinPoint joinPoint) {
        try {
            Tool annotation = joinPoint.getTarget().getClass()
                    .getMethod(joinPoint.getSignature().getName(),
                            ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getParameterTypes())
                    .getAnnotation(Tool.class);
            if (annotation != null && !annotation.name().isBlank()) {
                return annotation.name();
            }
        } catch (Exception ignored) {
            // 取不到注解时退回方法名，不影响主流程
        }
        return joinPoint.getSignature().getName();
    }

    /**
     * 从方法参数中找出 ToolContext。
     * <p>
     * Spring AI 会把 ToolContext 作为特殊参数注入（对模型不可见），
     * 因此这里按类型查找即可，不依赖参数位置。
     */
    private Optional<ToolContext> findToolContext(ProceedingJoinPoint joinPoint) {
        return Arrays.stream(joinPoint.getArgs())
                .filter(ToolContext.class::isInstance)
                .map(ToolContext.class::cast)
                .findFirst();
    }

    /**
     * 描述调用参数。
     * <p>
     * 只取业务参数（排除 ToolContext 这类框架注入的参数），
     * 否则轨迹里会出现一串无意义的上下文对象。
     */
    private String describeArgs(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            if (arg instanceof ToolContext || arg instanceof Map && isContextMap(arg)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(arg);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** 判断是否为框架注入的上下文 Map（含轮次标识键） */
    private boolean isContextMap(Object arg) {
        if (!(arg instanceof Map<?, ?> map)) {
            return false;
        }
        return map.containsKey(AgentTraceService.CTX_TURN_ID);
    }
}
