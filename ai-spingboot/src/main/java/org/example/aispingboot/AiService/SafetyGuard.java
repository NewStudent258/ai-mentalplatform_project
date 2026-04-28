package org.example.aispingboot.AiService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 安全护栏：识别「受限话题」并给出预置安全话术。
 * <p>
 * <b>为什么需要它</b>：大模型在缺乏依据时会产生「贴心的编造」——尤其面对用药、诊断这类
 * 问题，它可能给出看似专业的药名、剂量或结论。在心理健康场景下，这类编造可能造成真实伤害。
 * 因此对少数高风险话题，<b>不允许模型自由生成</b>，改用人工撰写、经过审校的固定话术。
 * <p>
 * <b>设计原则</b>：不确定的能力交给模型，确定的规则交回代码。
 * 这与项目中「量表计分不由模型计算」是同一套思路。
 * <p>
 * <b>三类受限话题</b>：
 * <ul>
 *   <li>{@link Category#MEDICATION} 用药咨询 —— 模型可能编造药名与剂量</li>
 *   <li>{@link Category#DIAGNOSIS} 自我诊断 —— 模型可能给出疾病判断</li>
 *   <li>{@link Category#SELF_HARM} 自伤倾向 —— 必须引导至专业求助渠道</li>
 * </ul>
 * <p>
 * <b>关于误判</b>：匹配采用「窄模式」，即只在用户明显在索取具体建议时才命中
 * （如「吃什么药」而非笼统的「药物」），以避免把「我不想吃药」这类正常倾诉误拦。
 * 即便如此仍可能有误判，因此话术统一写得温和、并留出继续对话的入口，
 * 即使误拦也不会让用户感到被冷落。
 */
@Component
public class SafetyGuard {

    private static final Logger log = LoggerFactory.getLogger(SafetyGuard.class);

    /** 受限话题分类 */
    public enum Category {
        /** 用药咨询 */
        MEDICATION,
        /** 自我诊断 */
        DIAGNOSIS,
        /** 自伤倾向 */
        SELF_HARM
    }

    /**
     * 判断模式：刻意保持「窄」，只在用户明确索取具体结论/建议时命中。
     * <p>
     * 例如「吃什么药」命中，而「我不想吃药」不命中——后者是正常的情绪倾诉，
     * 拦截它反而会破坏陪伴感。
     */
    private static final Map<Category, Pattern> PATTERNS = new LinkedHashMap<>();

    static {
        PATTERNS.put(Category.SELF_HARM, Pattern.compile(
                "想自杀|想死|不想活|活着没(意思|意义)|结束生命|自残|伤害自己|轻生|不如死"));

        PATTERNS.put(Category.MEDICATION, Pattern.compile(
                "(吃什么|该吃|能不能吃|可以吃|要吃什么).{0,6}药"
                        + "|药(量|物剂量)|(加|减|停)药"
                        + "|安眠药|抗抑郁药|抗焦虑药|镇静剂"));

        PATTERNS.put(Category.DIAGNOSIS, Pattern.compile(
                "(是不是|会不会|算不算|是不是得了).{0,6}(抑郁症|焦虑症|精神|心理)病?"
                        + "|我(得|有)了什么病|能帮我确诊"
                        + "|我是不是有病"));
    }

    /**
     * 危机求助资源。
     * <p>
     * 通过配置注入而非写死在代码里：不同学校、不同地区的求助渠道不同，
     * 部署方应改为本地实际可用的资源（如本校心理咨询中心电话）。
     */
    @Value("${safety.crisis-resources:学校心理咨询中心 · 全国心理援助热线 12356 · 紧急情况请拨 110/120}")
    private String crisisResources;

    /**
     * 检测文本是否命中受限话题。
     *
     * @return 命中的分类，未命中返回空
     */
    public Optional<Category> detect(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        for (Map.Entry<Category, Pattern> entry : PATTERNS.entrySet()) {
            if (entry.getValue().matcher(text).find()) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    /** 是否命中受限话题 */
    public boolean isRestricted(String text) {
        return detect(text).isPresent();
    }

    /**
     * 返回该分类对应的预置安全话术。
     * <p>
     * 话术设计的一致原则：<b>不冷冰冰地拒绝，而是承接情绪并给出明确出路</b>。
     * 心理支持场景下，生硬的「我不能回答这个问题」本身就是一种伤害。
     */
    public String response(Category category) {
        return switch (category) {
            case MEDICATION -> """
                    关于用药，我不能给你具体建议——每个人的身体状况不同，用药方案需要医生当面评估，\
                    任何脱离面诊的药名或剂量都可能带来风险。

                    如果你正在考虑用药，或者对正在服用的药有疑问，请咨询医生或学校医务室，\
                    他们会根据你的具体情况给出安全的方案。

                    不过我很想听听：是什么让你开始关注用药这件事？最近的睡眠或情绪，是不是让你不太好受？""";

            case DIAGNOSIS -> """
                    我不能给自己或他人下诊断——这需要专业医生结合面谈、量表和时间线综合判断，\
                    仅凭聊天内容下结论是不负责任的。

                    我能做的是陪你梳理最近的感受和状态。如果你觉得困扰已经持续了一段时间，\
                    学校心理咨询中心可以做专业的评估，那比自己猜测要可靠得多。

                    要不先跟我说说，最近具体是什么样的感觉，让你有这样的疑问？""";

            case SELF_HARM -> String.format("""
                    你刚才说的话，我听到了，也很在意你现在的感受。\
                    能把这些说出来，其实已经很不容易了。

                    你不需要一个人扛着这些。请一定要联系能真正帮到你的人：

                    %s

                    如果你愿意，也可以留在这里和我说说话——\
                    现在是什么让你这么难受？""", crisisResources);
        };
    }

    /**
     * 记录命中日志。
     * <p>
     * 受限话题命中需要留痕：既便于排查误判，也是心理产品的合规要求。
     */
    public void logHit(Category category, String scene) {
        log.warn("安全护栏命中受限话题：category={}，scene={}", category, scene);
    }
}
