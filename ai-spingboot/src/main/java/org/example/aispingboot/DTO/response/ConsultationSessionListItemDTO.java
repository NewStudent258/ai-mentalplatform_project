package org.example.aispingboot.DTO.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话列表项（前端会话列表面板所需字段）
 */
@Data
public class ConsultationSessionListItemDTO {
    // 会话ID
    private Long id;

    // 会话标题
    private String sessionTitle;

    // 开始时间
    private LocalDateTime startedAt;

    // 最后一条消息内容（预览）
    private String lastMessageContent;

    // 消息数量
    private Integer messageCount;

    // 会话时长（分钟）
    private Long durationMinutes;

    // ===== 以下字段供辅导员视角使用 =====
    // 学生端调用时不需要，留空即可；辅导员需要据此判断「这是谁的会话」

    // 归属学生ID
    private Long userId;

    // 归属学生姓名（冗余，避免前端再查一次）
    private String userName;
}
