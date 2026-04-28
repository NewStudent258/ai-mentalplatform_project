package org.example.aispingboot.AiService;

import cn.hutool.json.JSONUtil;
import org.example.aispingboot.DTO.response.EmotionAnalysisDTO;
import org.example.aispingboot.entity.ConsultationSession;
import org.example.aispingboot.service.ConsultationSessionService;
import org.example.aispingboot.service.CrisisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 情绪分析服务：把用户在对话中表达的情绪，转成前端「情绪花园」需要的数据。
 * <p>
 * 核心用的是 Spring AI 的 <b>Structured Output</b>——{@code call().entity(...)} 会依据
 * {@link EmotionAnalysisDTO} 生成 JSON Schema 交给模型，再把返回的 JSON 反序列化成对象，
 * 从而免去手写正则解析模型自由文本的麻烦。
 */
@Service
public class EmotionAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(EmotionAnalysisService.class);

    /** 风险等级取值区间 */
    private static final int MIN_RISK_LEVEL = 0;
    private static final int MAX_RISK_LEVEL = 3;
    /** 情绪分值区间 */
    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 100;

    private final ChatClient emotionAnalysisChatClient;
    private final ConsultationSessionService consultationSessionService;
    /** 识别到高风险时用于触发危机干预闭环（建事件、建工单） */
    private final CrisisService crisisService;

    public EmotionAnalysisService(
            @Qualifier("emotion-analysis") ChatClient emotionAnalysisChatClient,
            ConsultationSessionService consultationSessionService,
            CrisisService crisisService) {
        this.emotionAnalysisChatClient = emotionAnalysisChatClient;
        this.consultationSessionService = consultationSessionService;
        this.crisisService = crisisService;
    }

    /**
     * 分析用户本轮发言的情绪并落库。
     * <p>
     * 异步执行，且在方法内部吞掉所有异常：情绪分析是旁路增强能力，
     * 任何失败都只降级为「不更新情绪」，绝不能影响用户的主对话流程。
     *
     * @param sessionId   数据库会话ID
     * @param userMessage 用户本轮发言
     */
    @Async("aiAnalysisExecutor")
    public void analyzeAndSave(Long sessionId, String userMessage) {
        if (sessionId == null || !StringUtils.hasText(userMessage)) {
            return;
        }
        try {
            EmotionAnalysisDTO raw = emotionAnalysisChatClient.prompt()
                    .user(userMessage)
                    .call()
                    .entity(EmotionAnalysisDTO.class);

            if (raw == null) {
                log.warn("会话 {} 情绪分析返回空结果，保留上一次结果", sessionId);
                return;
            }

            EmotionAnalysisDTO result = normalize(raw);
            consultationSessionService.updateEmotionAnalysis(sessionId, toJson(result));

            log.info("会话 {} 情绪分析完成：{}（分值 {}，风险等级 {}）",
                    sessionId, result.primaryEmotion(), result.emotionScore(), result.riskLevel());

            // 高危结果触发危机干预闭环：写入危机事件并自动建单。
            // 改造前这里只打一条 ERROR 日志——识别是准的，但「打完日志就没有下文」，
            // 没有人被通知、没有记录、无人跟进。现在改为把识别结果落成可处理的工单。
            if (result.riskLevel() != null && result.riskLevel() >= CrisisService.CRISIS_RISK_THRESHOLD) {
                log.error("【风险预警】会话 {} 检测到风险等级 {}，情绪：{}",
                        sessionId, result.riskLevel(), result.primaryEmotion());
                reportCrisis(sessionId, userMessage, result);
            }
        } catch (Exception e) {
            // 注意：这里必须捕获 Exception 而不是 RuntimeException，
            // 大模型返回不合规 JSON 时抛出的可能是受检异常包装，漏接会让异步线程静默失败
            log.warn("会话 {} 情绪分析失败，保留上一次结果：{}", sessionId, e.getMessage());
        }
    }

    /**
     * 上报危机：写入事件并自动建单。
     * <p>
     * 单独包一层 try-catch，与情绪分析本身的失败隔离开：
     * 建单失败不应该让「情绪结果也不落库」，两者是独立的关注点。
     * 且危机上报属于旁路动作，任何异常都不能影响用户当前对话。
     */
    private void reportCrisis(Long sessionId, String userMessage, EmotionAnalysisDTO result) {
        try {
            // 工单需要记录是「哪个学生」，而情绪分析只拿到 sessionId，因此需回查会话
            ConsultationSession session = consultationSessionService.getById(sessionId);
            if (session == null) {
                log.warn("会话 {} 不存在，跳过危机建单", sessionId);
                return;
            }
            Long orderId = crisisService.reportCrisis(
                    session.getUserId(),
                    sessionId,
                    result.riskLevel(),
                    result.primaryEmotion(),
                    result.emotionScore(),
                    userMessage);

            if (orderId != null) {
                log.warn("会话 {} 已自动创建危机工单 {}，等待辅导员认领", sessionId, orderId);
            }
        } catch (Exception e) {
            // 建单失败会记录 error：这条链路直接关系到高危学生能否被及时跟进，
            // 静默失败意味着「系统以为已建单、实际没人知道」，后果严重
            log.error("会话 {} 危机建单失败，需人工核查：{}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * 校验并规整大模型返回的结果。
     * <p>
     * 大模型即使被 Schema 约束，仍可能给出越界值（如 riskLevel=5、分值 -10），
     * 这些值直接传给前端会造成 UI 异常（强度点渲染错乱、风险文案错配），
     * 因此在此统一钳制到合法区间并补齐空值。
     */
    private EmotionAnalysisDTO normalize(EmotionAnalysisDTO raw) {
        String emotion = StringUtils.hasText(raw.primaryEmotion()) ? raw.primaryEmotion() : "中性";

        Integer score = raw.emotionScore() == null ? 50 : raw.emotionScore();
        score = Math.max(MIN_SCORE, Math.min(MAX_SCORE, score));

        Integer risk = raw.riskLevel() == null ? 0 : raw.riskLevel();
        risk = Math.max(MIN_RISK_LEVEL, Math.min(MAX_RISK_LEVEL, risk));

        // 负向情绪与风险等级以模型判断为准，但风险等级 >= 2 时必须是负向，
        // 避免出现「风险预警 + 情绪积极」这种自相矛盾的状态
        boolean negative = Boolean.TRUE.equals(raw.isNegative()) || risk >= 2;

        String suggestion = StringUtils.hasText(raw.suggestion()) ? raw.suggestion() : "情绪状态平稳";

        List<String> actions = raw.improvementSuggestions() == null
                ? Collections.emptyList()
                : new ArrayList<>(raw.improvementSuggestions());

        // 风险提示只在真正有风险时输出，防止模型多话吓到用户
        String riskDescription = risk >= 2 && StringUtils.hasText(raw.riskDescription())
                ? raw.riskDescription()
                : "";

        return new EmotionAnalysisDTO(emotion, score, negative, risk, suggestion, actions, riskDescription);
    }

    /**
     * 转成前端约定的 JSON 结构。
     * <p>
     * 这里手工拼 Map 而不是直接序列化 record：字段名是前端「情绪花园」的既有契约，
     * 显式构造可以避免 Jackson 对 record 访问器的命名推断（isNegative -> negative）
     * 在序列化阶段再出一次问题。
     */
    private String toJson(EmotionAnalysisDTO dto) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("primaryEmotion", dto.primaryEmotion());
        map.put("emotionScore", dto.emotionScore());
        map.put("isNegative", dto.isNegative());
        map.put("riskLevel", dto.riskLevel());
        map.put("suggestion", dto.suggestion());
        map.put("improvementSuggestions", dto.improvementSuggestions());
        map.put("riskDescription", dto.riskDescription());
        return JSONUtil.toJsonStr(map);
    }
}
