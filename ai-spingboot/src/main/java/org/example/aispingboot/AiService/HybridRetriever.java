package org.example.aispingboot.AiService;

import org.example.aispingboot.entity.KnowledgeArticle;
import org.example.aispingboot.service.KnowledgeArticleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 混合检索：向量召回 + 关键词召回，用 RRF 融合两路排名。
 * <p>
 * <b>为什么需要两路</b>：两种检索各有盲区——
 * <ul>
 *   <li><b>向量检索</b>擅长语义相近但用词不同的查询（用户说「睡不着」、文章写「睡眠」），
 *       但对文章中的精确术语（如「4-7-8 呼吸法」）不敏感——术语在向量空间里
 *       未必比普通词更突出；</li>
 *   <li><b>关键词检索</b>正好相反：术语、专有名词一查一个准，但遇到词汇鸿沟就完全失效。</li>
 * </ul>
 * 两路互补，融合后理论上优于任何单路。
 * <p>
 * <b>为什么用 RRF 而不是加权求和</b>：
 * 向量相似度（0~1 的余弦值）与关键词得分（自定义的匹配计数）<b>量纲完全不同</b>，
 * 直接加权需要先把两者归一化，而归一化系数很难定——调参就变成了拍脑袋。
 * RRF（Reciprocal Rank Fusion）只使用<b>排名</b>而非分数，
 * 天然规避了量纲问题，是目前业界最常用的融合方法之一。
 * <p>
 * <b>关于 RRF 的常数 k</b>：公式为 {@code score = Σ 1/(k + rank)}，
 * k 取 60 是该方法的原始论文推荐值——它的作用是削弱排名靠前者的绝对优势，
 * 让两路结果都能进入融合，而不是被单路的前几名垄断。
 */
@Component
public class HybridRetriever {

    private static final Logger log = LoggerFactory.getLogger(HybridRetriever.class);

    /** RRF 公式中的常数 k，取原始论文推荐值 60 */
    private static final int RRF_K = 60;

    /** 每路召回的候选数量：应大于最终返回数，给融合留出腾挪空间 */
    private static final int CANDIDATE_MULTIPLIER = 3;

    /** 关键词打分的字段权重：标题权重最高，正文最低 */
    private static final double WEIGHT_TITLE = 3.0;
    private static final double WEIGHT_TAG = 2.0;
    private static final double WEIGHT_SUMMARY = 2.0;
    private static final double WEIGHT_CONTENT = 1.0;

    @Autowired
    private KnowledgeVectorStore knowledgeVectorStore;

    @Autowired
    private KnowledgeArticleService knowledgeArticleService;

    /** 检索模式：用于评测时对比不同方案 */
    public enum Mode {
        /** 仅向量检索 */
        VECTOR,
        /** 仅关键词检索 */
        KEYWORD,
        /** 混合检索（RRF 融合） */
        HYBRID
    }

    /**
     * 统一检索入口。
     *
     * @param mode 检索模式；传 null 时默认 HYBRID
     */
    public List<KnowledgeVectorStore.ScoredArticle> search(String query, int topK, Mode mode) {
        Mode actual = mode == null ? Mode.HYBRID : mode;
        int candidates = Math.max(topK * CANDIDATE_MULTIPLIER, 10);

        if (actual == Mode.VECTOR) {
            return knowledgeVectorStore.search(query, topK);
        }
        if (actual == Mode.KEYWORD) {
            List<Long> ids = keywordSearch(query, candidates);
            return toScoredArticles(ids.subList(0, Math.min(topK, ids.size())));
        }

        // HYBRID：两路召回后融合
        List<KnowledgeVectorStore.ScoredArticle> vectorHits = List.of();
        try {
            vectorHits = knowledgeVectorStore.search(query, candidates);
        } catch (Exception e) {
            // 向量路失败时退化为纯关键词——这正是「降级链」的思路
            log.warn("混合检索中向量路失败，降级为关键词检索：{}", e.getMessage());
        }
        List<Long> vectorIds = vectorHits.stream()
                .map(KnowledgeVectorStore.ScoredArticle::articleId).toList();
        List<Long> keywordIds = keywordSearch(query, candidates);

        log.debug("混合检索：query={}，向量路 {} 条，关键词路 {} 条",
                query, vectorIds.size(), keywordIds.size());

        List<Long> fused = rrfFuse(vectorIds, keywordIds);
        return toScoredArticles(fused.subList(0, Math.min(topK, fused.size())));
    }

    /**
     * RRF 融合：{@code score(doc) = Σ 1 / (K + rank_i(doc))}
     * <p>
     * 只使用排名信息，因此不关心两路分数的量纲差异。
     * 同时出现在两路且排名都靠前的文档会得到最高分。
     */
    private List<Long> rrfFuse(List<Long> vectorIds, List<Long> keywordIds) {
        Map<Long, Double> scores = new LinkedHashMap<>();

        for (int i = 0; i < vectorIds.size(); i++) {
            Long id = vectorIds.get(i);
            if (id != null) {
                scores.merge(id, 1.0 / (RRF_K + i + 1), Double::sum);
            }
        }
        for (int i = 0; i < keywordIds.size(); i++) {
            scores.merge(keywordIds.get(i), 1.0 / (RRF_K + i + 1), Double::sum);
        }

        List<Long> result = new ArrayList<>(scores.keySet());
        // 按融合分降序；分数相同时按 ID 升序，保证结果稳定可复现
        result.sort(Comparator.comparingDouble((Long id) -> scores.get(id)).reversed()
                .thenComparing(Comparator.naturalOrder()));
        return result;
    }

    /**
     * 关键词检索：用中文 2-gram 匹配 + 字段加权打分。
     * <p>
     * <b>为什么用 2-gram 而不是分词</b>：中文分词需要引入分词器（如 IK、jieba），
     * 而知识库只有几十篇文章、检索词也短，2-gram（连续两字切分）已经能覆盖绝大部分匹配需求，
     * 引入分词器属于为了效果边际提升而增加依赖。
     * <p>
     * <b>为什么不用 SQL LIKE</b>：LIKE 只能判断「包含或不包含」，
     * 无法给出相关性排序——所有匹配的文档得分相同，排序只能靠阅读量这类无关指标。
     * 这里的做法是查出候选后在内存里按匹配度打分，文档量小，代价可忽略。
     */
    private List<Long> keywordSearch(String query, int limit) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        Set<String> grams = bigrams(query);
        if (grams.isEmpty()) {
            return List.of();
        }

        List<KnowledgeArticle> articles;
        try {
            articles = knowledgeArticleService.listPublishedForSearch();
        } catch (Exception e) {
            log.warn("关键词检索取文章失败：{}", e.getMessage());
            return List.of();
        }

        Map<Long, Double> scored = new HashMap<>();
        for (KnowledgeArticle article : articles) {
            double score = scoreArticle(article, grams);
            if (score > 0) {
                scored.put(article.getId(), score);
            }
        }

        return scored.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed()
                        // 得分相同按 ID 升序，保证排序稳定
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** 按字段权重累加该文章命中的 gram 数 */
    private double scoreArticle(KnowledgeArticle article, Set<String> grams) {
        double score = 0;
        score += countHits(article.getTitle(), grams) * WEIGHT_TITLE;
        score += countHits(article.getTags(), grams) * WEIGHT_TAG;
        score += countHits(article.getSummary(), grams) * WEIGHT_SUMMARY;
        score += countHits(article.getContent(), grams) * WEIGHT_CONTENT;
        return score;
    }

    /** 统计 text 中命中的 gram 数量（去重：同一 gram 重复出现只计一次，避免长文本刷分） */
    private int countHits(String text, Set<String> grams) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        int hits = 0;
        for (String gram : grams) {
            if (text.contains(gram)) {
                hits++;
            }
        }
        return hits;
    }

    /**
     * 生成中文 2-gram。
     * <p>
     * 同时保留单字：有些查询只有一个字（如「累」），此时 2-gram 为空，
     * 只靠 2-gram 会完全没有结果。
     */
    private Set<String> bigrams(String text) {
        String cleaned = text.replaceAll("\\s+", "");
        Set<String> grams = new LinkedHashSet<>();
        for (int i = 0; i < cleaned.length() - 1; i++) {
            grams.add(cleaned.substring(i, i + 2));
        }
        // 单字查询兜底
        if (grams.isEmpty() && !cleaned.isEmpty()) {
            grams.add(cleaned);
        }
        return grams;
    }

    /** 把文章ID列表转成 ScoredArticle（用于统一返回结构） */
    private List<KnowledgeVectorStore.ScoredArticle> toScoredArticles(List<Long> ids) {
        List<KnowledgeVectorStore.ScoredArticle> result = new ArrayList<>(ids.size());
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            KnowledgeVectorStore.ScoredArticle item = knowledgeVectorStore.loadMeta(id);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    /** 供测试与调试：查看某条查询在关键词路上的命中情况 */
    public Set<String> debugBigrams(String query) {
        return new HashSet<>(bigrams(query));
    }
}
