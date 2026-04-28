package org.example.aispingboot.DTO.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 危机工单展示对象：工单信息 + 关联的危机事件信息。
 * <p>
 * 两者合并返回，是因为辅导员打开工单时必须立刻知道「为什么建的单」——
 * 触发时的情绪、风险等级与那句话，否则无从判断该如何介入。
 */
@Data
public class CrisisWorkOrderDTO {

    // ===== 工单信息 =====
    private Long id;
    private Long eventId;
    private Long userId;
    /** 来源编码，见 WorkOrderSource */
    private String source;
    /** 来源描述：AI识别高危 / Agent主动转介 */
    private String sourceDesc;
    /** 紧急程度 1:高 2:中 3:低 */
    private Integer urgency;
    /** 转介原因（非 AI 识别场景） */
    private String escalateReason;
    private Integer status;
    /** 状态描述，避免前端硬编码枚举文案 */
    private String statusDesc;
    private Long handlerId;
    private String handlerName;
    private LocalDateTime claimedAt;
    private LocalDateTime closedAt;
    private String handleResult;
    private LocalDateTime createdAt;

    // ===== 关联的危机事件信息 =====
    private Integer riskLevel;
    private String primaryEmotion;
    private Integer emotionScore;
    /** 触发建单的那句话（留痕），供辅导员判断介入方式 */
    private String triggerMessage;
    private Long sessionId;
    private LocalDateTime eventCreatedAt;
}
