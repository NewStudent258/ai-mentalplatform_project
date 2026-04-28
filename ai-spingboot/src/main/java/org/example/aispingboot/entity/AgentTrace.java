package org.example.aispingboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 执行轨迹：记录一次对话中 Agent 做了什么。
 * <p>
 * <b>为什么需要它</b>：Agent 与普通问答机器人的本质区别在于「为了达成目标而执行多步动作」。
 * 如果这些动作不可见，系统就退化成一个黑盒——出了问题只能看到「回复不对」，
 * 却不知道是没检索、检索错了、还是判断失误。
 * <p>
 * 本表把 Agent 的每一步（工具调用、生成）留下来，用于：
 * <ul>
 *   <li><b>问题定位</b>：回答不对时，回看它到底调了哪些工具、拿到什么结果</li>
 *   <li><b>效果评估</b>：统计工具命中率、平均步数、耗时分布</li>
 *   <li><b>安全审计</b>：确认受限话题的处置路径是否符合预期</li>
 * </ul>
 * <p>
 * 一轮用户发言对应一个 {@code turnId}，轮次内的多个步骤共享该标识并按 {@code stepIndex} 排序。
 */
@Data
@TableName("agent_trace")
@Builder
public class AgentTrace {

    /** 步骤类型：轮次开始 */
    public static final String TYPE_TURN_START = "TURN_START";
    /** 步骤类型：工具调用 */
    public static final String TYPE_TOOL_CALL = "TOOL_CALL";
    /** 步骤类型：内容生成 */
    public static final String TYPE_GENERATION = "GENERATION";
    /** 步骤类型：安全拦截 */
    public static final String TYPE_SAFETY_BLOCK = "SAFETY_BLOCK";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 轮次标识：一次用户发言对应一个轮次 */
    @TableField("turn_id")
    private String turnId;

    @TableField("session_id")
    private Long sessionId;

    @TableField("user_id")
    private Long userId;

    /** 步骤序号，轮次内从 0 递增 */
    @TableField("step_index")
    private Integer stepIndex;

    /** 步骤类型，见本类常量 */
    @TableField("step_type")
    private String stepType;

    /** 工具名（仅 TOOL_CALL 有值） */
    @TableField("tool_name")
    private String toolName;

    /** 工具入参 */
    @TableField("tool_input")
    private String toolInput;

    /** 结果摘要（截断保存，避免轨迹表被超长内容撑大） */
    @TableField("result_summary")
    private String resultSummary;

    /** 耗时（毫秒） */
    @TableField("duration_ms")
    private Long durationMs;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
