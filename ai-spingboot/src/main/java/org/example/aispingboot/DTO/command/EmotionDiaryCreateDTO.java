package org.example.aispingboot.DTO.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class EmotionDiaryCreateDTO {
    // 日记日期
    @NotNull(message = "日记日期不能为空")
    private LocalDate diaryDate;

    // 情绪评分1-10
    @NotNull(message = "情绪评分不能为空")
    @Min(value = 1, message = "情绪评分最小为1")
    @Max(value = 10, message = "情绪评分最大为10")
    private Integer moodScore;

    // 主要情绪
    @Size(max = 50, message = "主要情绪长度不能超过50个字符")
    private String dominantEmotion;

    // 情绪触发因素
    @Size(max = 1000, message = "情绪触发因素长度不能超过1000个字符")
    private String emotionTriggers;

    // 日记内容
    @Size(max = 2000, message = "日记内容长度不能超过2000个字符")
    private String diaryContent;

    // 睡眠质量1-5
    @Min(value = 1, message = "睡眠质量最小为1")
    @Max(value = 5, message = "睡眠质量最大为5")
    private Integer sleepQuality;

    // 压力水平1-5
    @Min(value = 1, message = "压力水平最小为1")
    @Max(value = 5, message = "压力水平最大为5")
    private Integer stressLevel;
}
