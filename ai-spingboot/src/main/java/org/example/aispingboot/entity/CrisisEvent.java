package org.example.aispingboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 危机事件（AI 识别的留痕，写入后不再修改）。
 * <p>
 * 与 {@link CrisisWorkOrder} 的分工：本表回答「发生了什么」，记录 AI 的判定结果与触发上下文；
 * 工单表回答「谁在处理」，承载认领、处置、闭环等可变状态。
 * <p>
 * 之所以不合并为一张表：事件是不可变的历史记录，工单是不断变化的工作流。
 * 合并会导致「更新工单状态时连带改写事件记录」，破坏留痕的可信度——
 * 而危机处置恰恰是最需要可追溯的场景。
 */
@Data
@TableName("crisis_event")
@Builder
public class CrisisEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学生用户ID */
    @TableField("user_id")
    private Long userId;

    /** 触发会话ID */
    @TableField("session_id")
    private Long sessionId;

    /** 风险等级 2:预警 3:危机 */
    @TableField("risk_level")
    private Integer riskLevel;

    /** 触发时的主要情绪 */
    @TableField("primary_emotion")
    private String primaryEmotion;

    /** 触发时的情绪分值 */
    @TableField("emotion_score")
    private Integer emotionScore;

    /**
     * 触发的用户消息原文。
     * <p>
     * 保留原文是必要的：事后回溯时需要知道「AI 究竟是基于什么判定为高危的」，
     * 否则无法评估误报、也无法复盘识别质量。这属于危机干预的合规留痕，
     * 访问权限限定为辅导员角色。
     */
    @TableField("trigger_message")
    private String triggerMessage;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
