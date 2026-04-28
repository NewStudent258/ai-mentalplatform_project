package org.example.aispingboot.AiService.tool;

import cn.hutool.http.HtmlUtil;
import org.example.aispingboot.AiService.KnowledgeVectorStore;
import org.example.aispingboot.AiService.SafetyGuard;
import org.example.aispingboot.entity.KnowledgeArticle;
import org.example.aispingboot.service.KnowledgeArticleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 站内知识库检索工具（Spring AI Tool Calling）。
 * <p>
 * 让 AI 在对话中能主动检索并引用站内的专业心理文章。
 * <p>
 * <b>检索方式演进记录</b>：本工具最初基于数据库关键词 LIKE 匹配，实测发现存在词汇鸿沟——
 * 用户说「失眠」，而文章标题是《改善睡眠质量的五个习惯》，字面重合度为 0，检索命中率为 0。
 * 因此改用向量语义检索（{@link KnowledgeVectorStore}），
 * 实测「失眠睡不着怎么办」与《改善睡眠质量的五个习惯》相似度 0.618，正确命中。
 * <p>
 * <b>降级链设计</b>：单一检索手段不可靠——向量检索依赖外部 Embedding 服务，
 * 服务抖动、限流、网络异常都会导致不可用。因此设计三级降级：
 * <pre>
 *   ① 向量语义检索（主）
 *        ↓ 服务异常，或结果为空 / 全部低于相似度阈值
 *   ② 关键词检索（MySQL LIKE）
 *        ↓ 仍无结果
 *   ③ 明确告知「无相关依据」，并禁止编造
 * </pre>
 * 第三级尤为关键：心理场景下，「查不到就自由发挥」比「查不到」危险得多。
 */
@Component
public class KnowledgeSearchTool {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSearchTool.class);

    /** 最多返回几篇文章：条数过多会挤占上下文，反而稀释重点 */
    private static final int MAX_RESULTS = 3;
    /**
     * 相似度下限。低于该值说明语义上并不相关，
     * 强行塞给模型会诱导它生搬硬套，反而降低回答质量。
     */
    private static final double MIN_SCORE = 0.35;

    private final KnowledgeVectorStore knowledgeVectorStore;
    private final KnowledgeArticleService knowledgeArticleService;
    private final SafetyGuard safetyGuard;

    public KnowledgeSearchTool(KnowledgeVectorStore knowledgeVectorStore,
                               KnowledgeArticleService knowledgeArticleService,
                               SafetyGuard safetyGuard) {
        this.knowledgeVectorStore = knowledgeVectorStore;
        this.knowledgeArticleService = knowledgeArticleService;
        this.safetyGuard = safetyGuard;
    }

    /**
     * 语义检索站内心理健康知识库。
     * <p>
     * description 是写给模型看的「何时该调用」说明，直接影响工具命中率，需要写清楚适用场景。
     */
    @Tool(name = "searchKnowledge",
            description = "检索站内的心理健康知识库，获取专业的心理科普文章。"
                    + "当用户描述具体的心理困扰时使用，例如失眠、焦虑、压力大、情绪低落、"
                    + "人际关系困扰、自我成长等，用来查找站内已有的专业文章并结合其内容回答用户。"
                    + "不要在闲聊、寒暄或与心理健康无关的话题上调用。")
    public String searchKnowledge(
            @ToolParam(description = "用自然语言描述要检索的内容，例如「失眠睡不着」「工作压力大」")
            String query,
            /*
             * ToolContext 由 Spring AI 注入，对模型不可见（不参与工具签名）。
             * 本方法自身并不使用它，但必须声明该参数——
             * ToolTraceAspect 正是通过它拿到轮次上下文，从而记录本次调用的执行轨迹。
             * 换言之，声明它是为了让切面能够统一埋点，而不必在每个工具里手写记录代码。
             */
            ToolContext toolContext) {

        if (!StringUtils.hasText(query)) {
            return "未提供检索内容，请直接用你自己的专业知识回答用户。";
        }

        // ===== 第①级：向量语义检索 =====
        List<KnowledgeVectorStore.ScoredArticle> vectorHits = List.of();
        boolean vectorFailed = false;
        try {
            vectorHits = knowledgeVectorStore.search(query, MAX_RESULTS);
        } catch (Exception e) {
            // 不中断对话：记录后转入关键词降级
            vectorFailed = true;
            log.warn("向量检索不可用，降级至关键词检索。query={}，原因：{}", query, e.getMessage());
        }

        List<KnowledgeVectorStore.ScoredArticle> relevant = vectorHits.stream()
                .filter(h -> h.score >= MIN_SCORE)
                .toList();

        if (!relevant.isEmpty()) {
            log.info("知识库检索（向量）：query={}，召回 {} 篇，达标 {} 篇，最高分 {}",
                    query, vectorHits.size(), relevant.size(),
                    String.format("%.4f", vectorHits.get(0).score));
            return buildArticlesMessage(relevant);
        }

        // ===== 第②级：关键词检索降级 =====
        // 向量检索「结果为空」与「服务异常」都要降级：前者可能只是语义没匹配上，
        // 但字面是匹配的（例如专有名词、文章标题中的原词）
        List<KnowledgeArticle> keywordHits = keywordSearch(query);
        if (!keywordHits.isEmpty()) {
            log.info("知识库检索（关键词降级）：query={}，命中 {} 篇{}",
                    query, keywordHits.size(), vectorFailed ? "（向量检索不可用）" : "（向量无达标结果）");
            return buildArticlesMessageFromEntities(keywordHits);
        }

        // ===== 第③级：无依据 =====
        log.info("知识库检索无结果：query={}（向量达标 0，关键词 0）", query);
        return buildNoEvidenceMessage(query);
    }

    /**
     * 关键词降级检索。
     * <p>
     * 复用已有的 {@link KnowledgeArticleService#searchPublished}（MySQL LIKE 匹配），
     * 而不是引入 BM25 倒排索引——知识库规模只有几十篇，为它维护一套倒排索引属于过度设计，
     * 而 LIKE 在中文短文本上的表现与 BM25 差距很小。
     */
    private List<KnowledgeArticle> keywordSearch(String query) {
        try {
            List<KnowledgeArticle> hits = knowledgeArticleService.searchPublished(query, MAX_RESULTS);
            return hits == null ? List.of() : hits;
        } catch (Exception e) {
            log.warn("关键词降级检索同样失败，query={}：{}", query, e.getMessage());
            return List.of();
        }
    }

    /** 渲染向量检索结果（含出处链接） */
    private String buildArticlesMessage(List<KnowledgeVectorStore.ScoredArticle> hits) {
        StringBuilder sb = new StringBuilder();
        sb.append("站内知识库中找到 ").append(hits.size()).append(" 篇相关文章：\n");
        for (int i = 0; i < hits.size(); i++) {
            KnowledgeVectorStore.ScoredArticle hit = hits.get(i);
            sb.append("\n【").append(i + 1).append("】").append(articleLink(hit.getTitle(), hit.articleId()));
            if (StringUtils.hasText(hit.getCategoryName())) {
                sb.append("（分类：").append(hit.getCategoryName()).append("）");
            }
            sb.append("\n");
            if (StringUtils.hasText(hit.getExcerpt())) {
                sb.append("正文节选：").append(hit.getExcerpt()).append("\n");
            }
        }
        sb.append("\n请结合上述文章内容回答用户；引用时请使用上面给出的 Markdown 链接格式，"
                + "以便用户直接跳转阅读全文。如果文章内容与用户的问题并不相关，就忽略它，不要生搬硬套。");
        return sb.toString();
    }

    /** 渲染关键词降级结果（含出处链接） */
    private String buildArticlesMessageFromEntities(List<KnowledgeArticle> hits) {
        StringBuilder sb = new StringBuilder();
        sb.append("站内知识库中找到 ").append(hits.size()).append(" 篇相关文章：\n");
        for (int i = 0; i < hits.size(); i++) {
            KnowledgeArticle article = hits.get(i);
            sb.append("\n【").append(i + 1).append("】").append(articleLink(article.getTitle(), article.getId()));
            if (StringUtils.hasText(article.getCategoryName())) {
                sb.append("（分类：").append(article.getCategoryName()).append("）");
            }
            sb.append("\n");
            if (StringUtils.hasText(article.getSummary())) {
                sb.append("摘要：").append(article.getSummary()).append("\n");
            }
        }
        sb.append("\n请结合上述文章内容回答用户；引用时请使用上面给出的 Markdown 链接格式，"
                + "以便用户直接跳转阅读全文。");
        return sb.toString();
    }

    /**
     * 生成文章出处链接。
     * <p>
     * 输出为 Markdown 链接，前端 MarkdownRenderer 会渲染成可点击跳转的锚点，
     * 用户点一下就能看到全文——比只给出标题更实用。
     */
    private String articleLink(String title, Object articleId) {
        if (articleId == null) {
            return "《" + title + "》";
        }
        return "[《" + title + "》](/knowledge/article/" + articleId + ")";
    }

    /**
     * 第③级：无检索依据时的响应。
     * <p>
     * 这里按「是否受限话题」分流，是本方法存在的核心意义：
     * <ul>
     *   <li><b>普通话题</b>：允许模型凭自身专业知识回答（模型本身有大量心理科普知识）</li>
     *   <li><b>受限话题</b>（用药 / 诊断 / 自伤）：<b>禁止模型自由生成</b>，
     *       直接返回预置安全话术。这类问题恰恰是模型最容易「贴心编造」的地方，
     *       而编造的药名、剂量、诊断结论可能造成真实伤害</li>
     * </ul>
     */
    private String buildNoEvidenceMessage(String query) {
        // 受限话题：不交给模型，直接给预置话术
        var restricted = safetyGuard.detect(query);
        if (restricted.isPresent()) {
            SafetyGuard.Category category = restricted.get();
            safetyGuard.logHit(category, "knowledge-search-no-evidence");
            return "站内知识库中没有相关依据。请【原样转述】以下内容给用户，不要自行补充或改写：\n\n"
                    + safetyGuard.response(category);
        }

        // 普通话题：可以把站内已有主题给模型作参考，但必须明确禁止编造
        StringBuilder sb = new StringBuilder();
        sb.append("站内知识库中没有找到与「").append(query).append("」相关的文章。\n");

        List<KnowledgeArticle> available = listAvailableArticles();
        if (!available.isEmpty()) {
            Set<String> titles = available.stream()
                    .map(KnowledgeArticle::getTitle)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet());
            sb.append("站内现有文章主题如下：\n");
            titles.forEach(t -> sb.append("- 《").append(t).append("》\n"));
            sb.append("如果其中某篇与用户的问题相关，可以用更贴合它的说法再调用一次本工具，最多重试一次；\n");
        }
        sb.append("如果确实都不相关，请基于你自己的专业知识回答用户，"
                + "但【不要编造、也不要向用户提及任何不存在的站内文章】。");
        return sb.toString();
    }

    /** 列出站内现有文章，仅用于无依据时给模型提供线索 */
    private List<KnowledgeArticle> listAvailableArticles() {
        try {
            List<KnowledgeArticle> articles = knowledgeArticleService.searchPublished(null, MAX_RESULTS);
            return articles == null ? List.of() : articles;
        } catch (Exception e) {
            log.warn("列出站内文章失败：{}", e.getMessage());
            return List.of();
        }
    }
}
