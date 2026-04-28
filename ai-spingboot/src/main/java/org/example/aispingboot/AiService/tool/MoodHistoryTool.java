package org.example.aispingboot.AiService.tool;

import org.example.aispingboot.AiService.context.AgentContext;
import org.example.aispingboot.entity.EmotionDiary;
import org.example.aispingboot.service.EmotionDiaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * 情绪历史查询工具。
 * <p>
 * <b>它补上了什么能力</b>：在此之前，用户问「我最近情绪怎么样」时，
 * AI 只能给出泛泛的安慰——因为它根本看不到用户的历史记录。
 * 有了这个工具，AI 可以基于真实数据回答，例如「你这周记录了 5 次，
 * 平均 4.2 分，比上周低了一些，主要是考试相关的焦虑」。
 * <p>
 * <b>数据归属</b>：查询对象来自 {@link AgentContext#USER_ID}（当前对话的学生），
 * 而非模型传入的参数——否则模型可能被诱导查询他人的记录。
 * 这是本工具最重要的安全设计：<b>身份只能来自服务端上下文，绝不能来自模型</b>。
 */
@Component
public class MoodHistoryTool {

    private static final Logger log = LoggerFactory.getLogger(MoodHistoryTool.class);

    /** 默认查询天数 */
    private static final int DEFAULT_DAYS = 7;
    /** 单次返回给模型的最大记录条数，避免历史过长挤占上下文 */
    private static final int MAX_RECORDS = 10;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM-dd");

    private final EmotionDiaryService emotionDiaryService;

    public MoodHistoryTool(EmotionDiaryService emotionDiaryService) {
        this.emotionDiaryService = emotionDiaryService;
    }

    @Tool(name = "queryMoodHistory",
            description = "查询当前用户最近的情绪日记记录，用于回答「我最近情绪怎么样」「我这几周状态如何」"
                    + "这类需要结合历史数据的问题。返回心情评分、主要情绪与触发因素。"
                    + "当用户询问自己的情绪变化趋势、近期状态时使用；与心理知识无关的问题不要调用。")
    public String queryMoodHistory(
            @ToolParam(description = "查询最近多少天的记录，不传则默认 7 天", required = false)
            Integer days,
            ToolContext toolContext) {

        Long userId = AgentContext.getLong(toolContext, AgentContext.USER_ID);
        if (userId == null) {
            // 取不到用户身份时明确告知，而不是返回空数据让模型误以为「用户没有记录」
            log.warn("情绪历史查询缺少用户上下文，已跳过");
            return "无法确定当前用户身份，暂时查不到历史记录。请不要编造用户的情绪数据，"
                    + "可以邀请用户先记录一条情绪日记。";
        }

        int range = (days == null || days <= 0) ? DEFAULT_DAYS : Math.min(days, 90);
        LocalDate since = LocalDate.now().minusDays(range);

        List<EmotionDiary> diaries;
        try {
            diaries = emotionDiaryService.listByUserId(userId);
        } catch (Exception e) {
            log.warn("查询用户 {} 情绪历史失败：{}", userId, e.getMessage());
            return "情绪记录暂时查询失败，请先基于用户当前说的话回应，不要编造历史数据。";
        }

        List<EmotionDiary> recent = diaries.stream()
                .filter(d -> d.getDiaryDate() != null && !d.getDiaryDate().isBefore(since))
                .sorted(Comparator.comparing(EmotionDiary::getDiaryDate).reversed())
                .toList();

        if (recent.isEmpty()) {
            return "用户在最近 " + range + " 天内没有情绪日记记录。"
                    + "请如实告知用户「这段时间还没看到你的记录」，并可以邀请 TA 记录一条；"
                    + "不要编造任何历史数据。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("用户最近 ").append(range).append(" 天共有 ").append(recent.size()).append(" 条情绪日记");
        if (recent.size() > MAX_RECORDS) {
            sb.append("（以下展示最近 ").append(MAX_RECORDS).append(" 条）");
        }
        sb.append("：\n");

        recent.stream().limit(MAX_RECORDS).forEach(d -> {
            sb.append("- ").append(d.getDiaryDate().format(DATE_FORMAT))
                    .append(" 心情 ").append(d.getMoodScore()).append("/10");
            if (StringUtils.hasText(d.getDominantEmotion())) {
                sb.append("，主要情绪：").append(d.getDominantEmotion());
            }
            if (d.getSleepQuality() != null) {
                sb.append("，睡眠 ").append(d.getSleepQuality()).append("/5");
            }
            if (d.getStressLevel() != null) {
                sb.append("，压力 ").append(d.getStressLevel()).append("/5");
            }
            if (StringUtils.hasText(d.getEmotionTriggers())) {
                sb.append("，诱因：").append(d.getEmotionTriggers());
            }
            sb.append("\n");
        });

        // 给出平均分与趋势判断，减少模型自行计算时出错的可能
        double avg = recent.stream()
                .filter(d -> d.getMoodScore() != null)
                .mapToInt(EmotionDiary::getMoodScore)
                .average().orElse(0);
        sb.append("平均心情分：").append(String.format("%.1f", avg)).append("/10");

        if (recent.size() >= 2) {
            // 按日期正序比较首尾，判断近期走向
            int newest = recent.get(0).getMoodScore() == null ? 0 : recent.get(0).getMoodScore();
            int oldest = recent.get(recent.size() - 1).getMoodScore() == null ? 0 : recent.get(recent.size() - 1).getMoodScore();
            if (newest - oldest >= 2) {
                sb.append("，整体呈好转趋势");
            } else if (oldest - newest >= 2) {
                sb.append("，整体呈下降趋势");
            } else {
                sb.append("，整体较为平稳");
            }
        }
        sb.append("。\n\n请基于以上真实数据回应，可以点出具体日期与变化；"
                + "不要编造未列出的记录，也不要下医学诊断。");
        return sb.toString();
    }
}
