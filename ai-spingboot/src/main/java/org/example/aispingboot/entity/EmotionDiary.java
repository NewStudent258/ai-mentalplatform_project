package org.example.aispingboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 情绪日记实体类
 */
@Data
@TableName("emotion_diary")
public class EmotionDiary {
    // 记录ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 用户ID
    @TableField("user_id")
    private Long userId;

    // 日记日期
    @TableField("diary_date")
    private LocalDate diaryDate;

    // 情绪评分1-10
    @TableField("mood_score")
    private Integer moodScore;

    // 主要情绪
    @TableField("dominant_emotion")
    private String dominantEmotion;

    // 情绪触发因素
    @TableField("emotion_triggers")
    private String emotionTriggers;

    // 日记内容
    @TableField("diary_content")
    private String diaryContent;

    // 睡眠质量1-5
    @TableField("sleep_quality")
    private Integer sleepQuality;

    // 压力水平1-5
    @TableField("stress_level")
    private Integer stressLevel;

    // 创建时间
    @TableField("created_at")
    private LocalDateTime createdAt;

    // 更新时间
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
