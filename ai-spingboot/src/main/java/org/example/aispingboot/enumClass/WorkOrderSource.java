package org.example.aispingboot.enumClass;

import lombok.Getter;

/**
 * 工单来源。
 * <p>
 * 区分来源的意义：同样是「需要辅导员跟进」，监管与复盘时的关注点不同——
 * AI 自动识别建单要评估<b>识别准确性</b>（有没有误报、漏报）；
 * Agent 主动转介要评估<b>判断合理性</b>（是不是过度转介、有没有该转未转）。
 * 混在一起统计，两个指标都会失真。
 */
@Getter
public enum WorkOrderSource {

    /** AI 情绪风险识别达到预警线后自动建单（有对应的危机事件） */
    AUTO_DETECTED("AI识别高危"),

    /**
     * Agent 在对话中判断需要人工介入，主动发起转介。
     * <p>
     * 与 AUTO_DETECTED 的区别：前者由风险等级驱动（系统规则），
     * 后者由 Agent 对具体处境的理解驱动（如学生表达了强烈的倾诉需求但未达高危线）。
     */
    AGENT_ESCALATED("Agent主动转介"),

    /** 学生明确要求找真人（如「我想预约咨询」） */
    STUDENT_REQUESTED("学生主动求助");

    private final String description;

    WorkOrderSource(String description) {
        this.description = description;
    }

    public static WorkOrderSource fromCode(String code) {
        for (WorkOrderSource source : WorkOrderSource.values()) {
            if (source.name().equals(code)) {
                return source;
            }
        }
        return AUTO_DETECTED;
    }
}
