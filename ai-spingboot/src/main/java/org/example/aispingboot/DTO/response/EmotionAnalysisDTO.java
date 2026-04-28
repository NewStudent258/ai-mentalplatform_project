package org.example.aispingboot.DTO.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 情绪分析结构化输出模型。
 * <p>
 * 作为 Spring AI Structured Output 的目标类型，由 {@code ChatClient.call().entity(...)}
 * 依据该 record 生成的 JSON Schema 约束大模型输出，并反序列化回来。
 * <p>
 * 字段名与前端「情绪花园」的契约保持一致，避免额外做一层字段映射。
 * 注意 {@code isNegative} 用 {@link JsonProperty} 显式固定名称：record 的
 * {@code isNegative()} 访问器会被 Jackson 误推断成 {@code negative}，导致反序列化拿到 null。
 */
public record EmotionAnalysisDTO(
        // 主要情绪：焦虑 / 悲伤 / 压力 / 平静 等
        @JsonProperty("primaryEmotion") String primaryEmotion,
        // 情绪分值 0-100，越高越积极，50 为中性
        @JsonProperty("emotionScore") Integer emotionScore,
        // 是否属于负向情绪
        @JsonProperty("isNegative") Boolean isNegative,
        // 风险等级 0-3：正常 / 关注 / 预警 / 危机
        @JsonProperty("riskLevel") Integer riskLevel,
        // 一句温暖可执行的建议
        @JsonProperty("suggestion") String suggestion,
        // 治愈小行动，1-3 条
        @JsonProperty("improvementSuggestions") List<String> improvementSuggestions,
        // 风险提示语，riskLevel >= 2 时才有内容
        @JsonProperty("riskDescription") String riskDescription
) {
}
