package org.example.aispingboot.AiService.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自助练习推荐工具。
 * <p>
 * <b>为什么练习内容预置在代码里，而不让模型自由生成</b>：
 * 这是本项目一贯的「受控工具」原则——<b>不确定的能力交给模型，确定的规则交回代码</b>。
 * 放松练习看似简单，但涉及具体的身心操作步骤（呼吸节奏、时长、顺序），
 * 一旦模型即兴发挥就可能给出不准确甚至有害的指导（例如让恐慌发作的人做长时间屏息）。
 * 因此这里维护一份经过筛选的练习库，模型只负责「根据用户情况选择哪一个」，
 * 不负责「编写练习内容」。
 * <p>
 * 这与受控测评（量表题目预置）、安全话术（话术预置）是同一套设计思路。
 * <p>
 * <b>关于扩展</b>：若后续需要支持运营配置练习内容，把 LIBRARY 迁移到数据库即可，
 * 工具签名与调用方无需改动。
 */
@Component
public class PracticeRecommendTool {

    private static final Logger log = LoggerFactory.getLogger(PracticeRecommendTool.class);

    /** 最多返回几个练习：过多会让用户不知从何下手，也给不出足够的执行细节 */
    private static final int MAX_RESULTS = 2;

    /** 一个自助练习 */
    private record Practice(String name, String duration, String steps) {
    }

    /**
     * 练习库：按适用场景归类。
     * <p>
     * 内容取自站内心理科普文章中的成熟方法（如 4-7-8 呼吸法、5-4-3-2-1 着陆法），
     * 均为公开且被广泛使用的放松技术。
     */
    private static final Map<List<String>, List<Practice>> LIBRARY = new LinkedHashMap<>();

    static {
        LIBRARY.put(List.of("失眠", "睡不着", "入睡", "睡眠", "半夜醒"),
                List.of(
                        new Practice("睡前清单法", "约 5 分钟", """
                                1. 在睡前半小时，找一张纸（不要用手机）
                                2. 把脑子里转的事情全部写下来：待办、担心、明天要做的
                                3. 写完后对自己说一句「今天到此为止，明天再处理」
                                4. 这个动作要在床以外的地方完成，让床和「思考」解绑"""),
                        new Practice("4-7-8 呼吸法", "约 2 分钟", """
                                1. 用鼻子吸气，默数 4 秒
                                2. 屏住呼吸，默数 7 秒
                                3. 用嘴缓慢呼气，默数 8 秒
                                4. 以上为一轮，重复 4 轮
                                注意：若感到头晕请立即恢复自然呼吸，不要勉强""")
                ));

        LIBRARY.put(List.of("焦虑", "紧张", "担心", "慌", "恐慌", "不安", "考试"),
                List.of(
                        new Practice("5-4-3-2-1 着陆法", "约 3 分钟", """
                                依次说出：
                                · 你看到的 5 样东西
                                · 你听到的 4 种声音
                                · 身体接触到的 3 种触感
                                · 闻到的 2 种气味
                                · 尝到的 1 种味道
                                这个方法能把注意力从「想」拉回「当下」"""),
                        new Practice("4-7-8 呼吸法", "约 2 分钟", """
                                1. 用鼻子吸气，默数 4 秒
                                2. 屏住呼吸，默数 7 秒
                                3. 用嘴缓慢呼气，默数 8 秒
                                4. 重复 4 轮
                                注意：若感到头晕请立即恢复自然呼吸""")
                ));

        LIBRARY.put(List.of("愤怒", "生气", "暴躁", "火大", "想骂"),
                List.of(
                        new Practice("离开现场 + 延长呼气", "约 5 分钟", """
                                1. 先离开让你生气的现场——身体的移动本身就能打断情绪升级
                                2. 站定后，吸气 4 秒、呼气 6 到 8 秒，做 5 轮
                                （呼气比吸气长，是让身体平静下来的关键）
                                3. 等强度降下来，再决定要不要沟通、怎么沟通"""),
                        new Practice("写下愤怒", "约 5 分钟", """
                                按这个句式写：
                                「我因为______感到愤怒，我其实希望______」
                                写的过程会推动大脑从情绪切换到思考""")
                ));

        LIBRARY.put(List.of("低落", "难过", "悲伤", "没劲", "提不起", "压抑"),
                List.of(
                        new Practice("微小行动", "约 10 分钟", """
                                1. 不要求自己「振作起来」，那只会增加压力
                                2. 只做一件小到不可能失败的事：喝杯水、拉开窗帘、走 5 分钟
                                3. 做完后在心里记一笔「我今天完成了一件事」"""),
                        new Practice("五分钟正念", "5 分钟", """
                                1. 坐下来，感受身体与椅子、地面的接触
                                2. 把注意力放在呼吸上，知道自己在吸气、在呼气
                                3. 走神时不要自责，轻轻说一句「哦，走神了」，再回到呼吸
                                走神本身不是失败，发现走神的那一刻，练习就已经在起作用""")
                ));

        LIBRARY.put(List.of("压力", "累", "喘不过气", "扛不住", "忙"),
                List.of(
                        new Practice("压力分层梳理", "约 10 分钟", """
                                先把压力写下来，再逐条归类：
                                · 能改变的 → 写下下一步具体行动
                                · 暂时改不了的 → 写下「我接受它现在这样」
                                · 担心但没发生的 → 写下「发生了我会怎么办」
                                模糊的压力最消耗人，写下来它就具体了"""),
                        new Practice("五分钟正念", "5 分钟", """
                                1. 坐下来，感受身体与椅子、地面的接触
                                2. 把注意力放在呼吸上，走神就温和地带回来
                                3. 不必刻意深呼吸，只要知道自己在呼吸""")
                ));
    }

    @Tool(name = "recommendPractice",
            description = "为用户推荐一个可立即执行的自助放松练习，例如呼吸法、着陆法、睡前清单等。"
                    + "当用户表达了具体的情绪困扰（焦虑、失眠、愤怒、低落、压力大）并希望得到缓解方法时使用。"
                    + "不要在用户只是想倾诉、或问题与情绪无关时调用。")
    public String recommendPractice(
            @ToolParam(description = "用户的困扰关键词，例如「失眠」「焦虑」「很生气」")
            String concern,
            ToolContext toolContext) {

        if (!StringUtils.hasText(concern)) {
            return "未提供困扰关键词。请不要自行编造练习内容，可以询问用户目前最想缓解的是什么。";
        }

        List<Practice> matched = match(concern);
        if (matched.isEmpty()) {
            // 匹配不到时不给「通用推荐」：宁可少说，也不让模型自由发挥练习步骤
            log.info("练习库未匹配到适合「{}」的练习", concern);
            return "练习库中没有与「" + concern + "」直接对应的练习。"
                    + "请用你自己的专业表达给出温和的陪伴与倾听，"
                    + "但【不要自行编写具体的呼吸节奏、身体操作步骤】，"
                    + "那类指导需要准确无误，不确定时不要给。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("为用户推荐以下自助练习（内容为预置标准步骤，请【原样转述】，不要自行增删或改写步骤）：\n");
        matched.forEach(p -> sb.append("\n【").append(p.name()).append("】（耗时约 ").append(p.duration()).append("）\n")
                .append(p.steps()).append("\n"));

        sb.append("\n请用温和的语气介绍，说明这个方法为什么对 TA 的情况有帮助；"
                + "如果用户表示做了之后更不舒服，请让 TA 立即停下。");
        return sb.toString();
    }

    /**
     * 按关键词匹配练习。
     * <p>
     * 采用包含匹配而非精确匹配：用户表达往往口语化（「晚上老是翻来覆去睡不着」），
     * 只要句中包含「睡不着」就能命中对应类别。
     */
    private List<Practice> match(String concern) {
        String text = concern.toLowerCase();
        for (Map.Entry<List<String>, List<Practice>> entry : LIBRARY.entrySet()) {
            for (String keyword : entry.getKey()) {
                if (text.contains(keyword)) {
                    List<Practice> practices = entry.getValue();
                    return practices.size() > MAX_RESULTS ? practices.subList(0, MAX_RESULTS) : practices;
                }
            }
        }
        return new ArrayList<>();
    }
}
