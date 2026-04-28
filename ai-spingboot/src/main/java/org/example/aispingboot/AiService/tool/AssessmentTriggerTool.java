package org.example.aispingboot.AiService.tool;

import org.example.aispingboot.AiService.context.AgentContext;
import org.example.aispingboot.entity.AssessmentScale;
import org.example.aispingboot.service.AssessmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 测评触发工具：由 Agent 判断「什么时候该建议用户做一次标准化测评」。
 * <p>
 * <b>这是「受控工具」原则最典型的一个例子</b>：
 * <ul>
 *   <li>Agent 负责判断时机——用户连续表达低落、焦虑且希望了解自己的状态时，建议做一次测评；</li>
 *   <li>题目与计分完全由服务端负责——模型既不能生成题目，也不能改写措辞或计算分数。</li>
 * </ul>
 * 原因在于 PHQ-9、GAD-7 是经过临床验证的标准化量表，题目的措辞与顺序都影响信效度。
 * 若让模型「用自己的话转述题目」，量表就失去参考价值，甚至可能因措辞偏差而误判风险。
 * <p>
 * <b>Agent 不能转述题目</b>：本工具只在用户的界面里推入一次待作答的测评，
 * 题目由前端从服务端拉取渲染。这样既保证题目原样呈现，也避免模型在对话中
 * 逐题提问（那样既冗长，又容易在复述时出错）。
 */
@Component
public class AssessmentTriggerTool {

    private static final Logger log = LoggerFactory.getLogger(AssessmentTriggerTool.class);

    private final AssessmentService assessmentService;

    public AssessmentTriggerTool(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @Tool(name = "triggerAssessment",
            description = "为用户推送一份标准化心理自评量表（PHQ-9 情绪状态 / GAD-7 焦虑状态）。"
                    + "当用户希望了解自己的情绪或焦虑程度（例如「我是不是抑郁了」「我想看看自己焦虑到什么程度」"
                    + "「有没有什么测试可以做」），且你判断适合做一次结构化自评时调用。"
                    + "注意：本工具只是把量表推送到用户界面，你【不要】在对话中逐题提问或转述题目内容。")
    public String triggerAssessment(
            @ToolParam(description = "量表类型：PHQ9 表示情绪状态自评，GAD7 表示焦虑状态自评")
            String scaleType,
            ToolContext toolContext) {

        Long userId = AgentContext.getLong(toolContext, AgentContext.USER_ID);
        Long sessionId = AgentContext.getLong(toolContext, AgentContext.SESSION_ID);

        if (userId == null) {
            log.warn("测评触发缺少用户上下文，已跳过");
            return "无法确定当前用户身份，未能推送测评。请改为用语言陪伴用户，不要自行提问量表题目。";
        }

        String code = normalizeScaleCode(scaleType);
        if (code == null) {
            // 编码非法时明确拒绝，绝不自行「编一套题」给用户
            return "未能识别量表类型（仅支持 PHQ9 与 GAD7）。请不要自行编造测评题目，"
                    + "可以改为了解用户的具体困扰并给出陪伴。";
        }

        try {
            Long recordId = assessmentService.startAssessment(userId, sessionId, code);
            String scaleName = assessmentService.getScale(code).getName();

            log.info("Agent 已为用户 {} 推送测评 {}，记录 {}", userId, code, recordId);
            return "已成功向用户推送《" + scaleName + "》，量表已在用户界面上弹出，用户可以直接作答。\n"
                    + "请这样告诉用户：\n"
                    + "1. 说明这次自评的作用（帮助更清楚地了解最近两周的状态，不是诊断）；\n"
                    + "2. 告知 TA 界面上已经出现量表，按最近两周的真实感受作答即可，答案没有对错；\n"
                    + "3. 【不要】在这里逐题提问或复述题目内容——题目已在界面上，复述会出错且冗长；\n"
                    + "4. 提醒作答只需一两分钟，做完后可以再回来一起看看结果。";
        } catch (Exception e) {
            log.error("推送测评失败：{}", e.getMessage(), e);
            return "测评推送失败。请如实告知用户当前无法发起自评，"
                    + "并继续用语言陪伴，【不要自行编造量表题目】。";
        }
    }

    /**
     * 归一化量表编码。
     * <p>
     * 模型可能返回 PHQ-9、phq9、PHQ9 等多种写法，统一转换为标准编码；
     * 无法识别时返回 null 由调用方明确拒绝，而不是猜测。
     */
    private String normalizeScaleCode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String cleaned = raw.toUpperCase(Locale.ROOT).replace("-", "").replace("_", "").trim();
        if (cleaned.contains("PHQ")) {
            return AssessmentScale.CODE_PHQ9;
        }
        if (cleaned.contains("GAD")) {
            return AssessmentScale.CODE_GAD7;
        }
        return null;
    }
}
