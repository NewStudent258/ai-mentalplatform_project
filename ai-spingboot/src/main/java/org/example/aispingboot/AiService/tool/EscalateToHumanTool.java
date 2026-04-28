package org.example.aispingboot.AiService.tool;

import org.example.aispingboot.AiService.context.AgentContext;
import org.example.aispingboot.service.CrisisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 转介人工工具：Agent 判断需要真人介入时，主动创建跟进工单。
 * <p>
 * <b>它体现的 Agent 自主性</b>：在此之前，系统只有一条固定的升级路径——
 * 情绪风险达到预警线时自动建单（规则驱动）。这个工具让 Agent 能够在
 * <b>未触发规则但理解到需要帮助</b>时主动行动。
 * 例如学生说「我还是想找个真人聊一聊」，情绪评估未必达线，
 * 但 Agent 应当识别出这个诉求并落实成一条工单，而不是回一句「你可以去预约」就结束。
 * <p>
 * <b>与自动建单的关系</b>：两者共用同一套去重逻辑（同一会话存在未闭环工单时不重复建单），
 * 因此不会出现「规则建了单、Agent 又建一张」的重复。
 * <p>
 * <b>绝不虚假承诺</b>：本工具如实返回创建结果。若建单失败，
 * 明确要求模型不要告知用户「已通知老师」——在心理健康场景下，
 * 让处于困境的学生误以为已经有人会联系自己，是比不转介更危险的事。
 */
@Component
public class EscalateToHumanTool {

    private static final Logger log = LoggerFactory.getLogger(EscalateToHumanTool.class);

    private final CrisisService crisisService;

    public EscalateToHumanTool(CrisisService crisisService) {
        this.crisisService = crisisService;
    }

    @Tool(name = "escalateToHuman",
            description = "当用户明确希望与真人交流（例如「我想找人聊聊」「能预约咨询吗」「有没有老师可以帮我」），"
                    + "或你所面对的困扰明显超出陪伴与科普能支持的范围时，调用本工具为用户创建人工跟进工单。"
                    + "注意：如果用户正处于高危状态（表达自伤、自杀想法），不要调用本工具——"
                    + "那类情况会由系统自动升级处理，你应当优先表达关心并引导求助渠道。")
    public String escalateToHuman(
            @ToolParam(description = "转介原因，简要说明为什么需要真人介入，例如「学生明确表达了想与真人交流的意愿」")
            String reason,
            @ToolParam(description = "紧急程度：1=高 2=中 3=低，不确定时不传", required = false)
            Integer urgency,
            ToolContext toolContext) {

        Long userId = AgentContext.getLong(toolContext, AgentContext.USER_ID);
        Long sessionId = AgentContext.getLong(toolContext, AgentContext.SESSION_ID);

        if (userId == null || sessionId == null) {
            log.warn("转介工具缺少上下文，已跳过");
            return "无法确定当前用户身份，未能创建跟进工单。"
                    + "请如实告知用户当前无法直接转接，并给出求助渠道（如学校心理咨询中心），"
                    + "【不要承诺已经通知老师】。";
        }

        String finalReason = StringUtils.hasText(reason) ? reason.trim() : "Agent 判断需要人工介入";

        try {
            Long orderId = crisisService.escalateToHuman(userId, sessionId, finalReason, urgency);

            if (orderId == null) {
                // 去重命中：该会话已有未闭环工单，说明已经有人会跟进
                return "当前会话已存在待处理的跟进工单，无需重复创建。"
                        + "请告知用户：已经有老师在跟进 TA 的情况，不必担心被忽略。";
            }

            log.info("Agent 已为学生 {} 创建转介工单 {}", userId, orderId);
            return "已成功创建人工跟进工单（工单号 " + orderId + "）。\n"
                    + "请用温和的语气告知用户：已经有心理老师在跟进，会在工作时间内联系 TA；"
                    + "如果此刻情况紧急或难以等待，可以立即拨打心理援助热线或联系学校心理咨询中心。";
        } catch (Exception e) {
            log.error("转介工单创建失败：{}", e.getMessage(), e);
            // 失败必须如实说明，绝不能给出「已通知」的假承诺
            return "人工跟进工单创建失败。请如实告知用户当前无法直接转接，"
                    + "并主动给出求助渠道（学校心理咨询中心 / 心理援助热线），"
                    + "【务必不要告知用户已经通知老师】。";
        }
    }
}
