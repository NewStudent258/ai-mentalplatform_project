package org.example.aispingboot.AiService;

import cn.hutool.http.HtmlUtil;
import cn.hutool.json.JSONUtil;
import org.example.aispingboot.entity.KnowledgeArticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 轻量向量存储：复用已有的 Redis，对知识库文章做语义检索。
 * <p>
 * 为什么不上专门的向量数据库（如 Milvus / Qdrant）：本项目知识库规模是「几十篇」量级，
 * 用 Redis 存储向量、在应用层算余弦相似度，检索延迟在毫秒级，完全够用。
 * 引入独立向量库会带来额外的部署、运维与一致性成本，属于当前阶段的过度设计。
 * 若日后文章涨到数万篇，再替换为 Redis 的向量索引或专用向量库，本类对外接口无需变动。
 * <p>
 * 存储结构（复用已有 Redis 实例，不新增中间件）：
 * <ul>
 *   <li>{@code kb:vec:{articleId}}  -> 文章向量（JSON 数组）</li>
 *   <li>{@code kb:meta:{articleId}} -> 文章元数据（标题/分类/摘要节选）</li>
 * </ul>
 */
@Component
public class KnowledgeVectorStore {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeVectorStore.class);

    private static final String VEC_KEY_PREFIX = "kb:vec:";
    private static final String META_KEY_PREFIX = "kb:meta:";

    /** 参与向量化的正文最大长度：超过部分截断，避免长文稀释语义重心、也控制调用成本 */
    private static final int MAX_EMBED_LENGTH = 800;
    /** 检索时进入模型上下文的摘要最大长度 */
    private static final int MAX_EXCERPT_LENGTH = 220;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 把文章写入向量库（存在则覆盖）。
     * <p>
     * 文章的增删改后都需要调用本方法重建索引，否则检索结果会与文章实际内容不一致。
     *
     * @return true 表示向量已成功写入；false 表示未写入（调用方据此记录失败，
     *         避免出现「文章显示已发布、AI 却检索不到」却无人察觉的情况）
     */
    public boolean index(KnowledgeArticle article) {
        if (article == null || article.getId() == null) {
            log.warn("跳过索引：文章或ID为空");
            return false;
        }
        String embedText = buildEmbedText(article);
        if (!StringUtils.hasText(embedText)) {
            log.warn("跳过索引：文章 {} 没有可向量化的内容", article.getId());
            return false;
        }

        List<Double> vector = embed(embedText);
        if (vector.isEmpty()) {
            // 走到这里说明 Embedding 调用失败。必须留下日志，
            // 否则该文章会永久性地不被 AI 检索到，且从表面上完全看不出异常
            log.error("文章 {} 《{}》 向量化失败，该文章暂时无法被 AI 检索到",
                    article.getId(), article.getTitle());
            return false;
        }

        String id = String.valueOf(article.getId());
        redisTemplate.opsForValue().set(VEC_KEY_PREFIX + id, JSONUtil.toJsonStr(vector));

        // 元数据与向量分开存：检索后渲染上下文只需要元数据，不必再查一次数据库
        Map<String, String> meta = Map.of(
                "id", id,
                "title", nullToEmpty(article.getTitle()),
                "categoryName", nullToEmpty(article.getCategoryName()),
                "excerpt", toPlainExcerpt(article.getContent())
        );
        redisTemplate.opsForValue().set(META_KEY_PREFIX + id, JSONUtil.toJsonStr(meta));
        return true;
    }

    /**
     * 判断某篇文章是否已建立索引。
     * <p>
     * 供启动时的对账使用：数据库里「已发布且可引用」的文章，
     * 若在 Redis 中找不到对应向量，说明曾因 Embedding 调用失败而漏建，
     * 需要补建——这类遗漏不会自我恢复，必须主动巡检。
     */
    public boolean isIndexed(Long articleId) {
        if (articleId == null) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(VEC_KEY_PREFIX + articleId));
    }

    /**
     * 从向量库移除文章
     */
    public void remove(Long articleId) {
        if (articleId == null) {
            return;
        }
        String id = String.valueOf(articleId);
        redisTemplate.delete(VEC_KEY_PREFIX + id);
        redisTemplate.delete(META_KEY_PREFIX + id);
    }

    /**
     * 语义检索：返回与 query 语义最接近的若干文章。
     * <p>
     * 这正是修复「用户说失眠、文章写睡眠导致关键词匹配为 0」的关键——向量检索比较的是
     * 语义相似度而非字面是否重合，因此能跨越同义词/近义词的鸿沟。
     */
    public List<ScoredArticle> search(String query, int topK) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        List<Double> queryVector = embed(query);
        if (queryVector.isEmpty()) {
            return List.of();
        }

        Set<String> vecKeys = redisTemplate.keys(VEC_KEY_PREFIX + "*");
        if (vecKeys == null || vecKeys.isEmpty()) {
            // 向量库为空（尚未建索引），返回空让上层降级，而不是抛异常中断对话
            return List.of();
        }

        List<ScoredArticle> scored = new ArrayList<>();
        for (String vecKey : vecKeys) {
            String id = vecKey.substring(VEC_KEY_PREFIX.length());
            String vecJson = redisTemplate.opsForValue().get(vecKey);
            String metaJson = redisTemplate.opsForValue().get(META_KEY_PREFIX + id);
            if (vecJson == null || metaJson == null) {
                continue;
            }
            List<Double> vector = parseVector(vecJson);
            if (vector.size() != queryVector.size()) {
                // 维度不一致说明是旧模型遗留的向量，跳过而不是算出错误的相似度
                continue;
            }
            ScoredArticle item = new ScoredArticle();
            item.id = id;
            item.meta = JSONUtil.parseObj(metaJson);
            item.score = cosine(queryVector, vector);
            // 元数据被手工删掉时标题缺失，跳过避免返回空标题的条目
            if (StringUtils.hasText(item.meta.getStr("title"))) {
                scored.add(item);
            }
        }

        scored.sort(Comparator.comparingDouble((ScoredArticle s) -> s.score).reversed());
        return scored.size() > topK ? scored.subList(0, topK) : scored;
    }

    /**
     * 把标题、分类、摘要、标签与正文拼成用于向量化的文本。
     * <p>
     * 刻意把标题与摘要放在最前：它们信息密度最高，对语义向量的影响也最直接。
     */
    private String buildEmbedText(KnowledgeArticle article) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(article.getTitle())) {
            sb.append(article.getTitle()).append("。");
        }
        if (StringUtils.hasText(article.getCategoryName())) {
            sb.append("分类：").append(article.getCategoryName()).append("。");
        }
        if (StringUtils.hasText(article.getTags())) {
            sb.append("标签：").append(article.getTags()).append("。");
        }
        if (StringUtils.hasText(article.getSummary())) {
            sb.append(article.getSummary()).append("。");
        }
        String plain = HtmlUtil.cleanHtmlTag(nullToEmpty(article.getContent())).replaceAll("\\s+", " ").trim();
        if (StringUtils.hasText(plain)) {
            sb.append(plain.length() > MAX_EMBED_LENGTH ? plain.substring(0, MAX_EMBED_LENGTH) : plain);
        }
        return sb.toString();
    }

    /** 调用向量化服务，失败返回空列表由上层降级处理 */
    /**
     * 调用向量化服务，失败返回空列表由上层降级处理。
     * <p>
     * <b>失败必须留日志</b>：这里如果把异常静默吞掉，运维侧就完全看不出
     * 「Embedding 服务挂了」——检索会退化成关键词匹配，而日志里只显示
     * 「向量无达标结果」，表现为「检索效果莫名变差」，极难定位。
     * 因此失败时记录 error 级别日志，使服务不可用与「语义未命中」可区分。
     */
    private List<Double> embed(String text) {
        try {
            float[] floats = embeddingModel.embed(text);
            List<Double> vector = new ArrayList<>(floats.length);
            for (float f : floats) {
                vector.add((double) f);
            }
            return vector;
        } catch (Exception e) {
            log.error("向量化调用失败，检索已降级至关键词匹配。原因：{}", e.getMessage());
            return List.of();
        }
    }

    private List<Double> parseVector(String json) {
        try {
            return JSONUtil.toList(JSONUtil.parseArray(json), Double.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 余弦相似度，取值 [-1, 1]，越大越相似 */
    private double cosine(List<Double> a, List<Double> b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private String toPlainExcerpt(String htmlContent) {
        String plain = HtmlUtil.cleanHtmlTag(nullToEmpty(htmlContent)).replaceAll("\\s+", " ").trim();
        return plain.length() > MAX_EXCERPT_LENGTH ? plain.substring(0, MAX_EXCERPT_LENGTH) + "…" : plain;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** 检索结果：文章元数据 + 相似度分数 */
    public static class ScoredArticle {
        public String id;
        public cn.hutool.json.JSONObject meta;
        public double score;

        /**
         * 文章ID（数值型）。
         * <p>
         * 供上层生成「阅读全文」链接使用——只给出标题，用户还得自己去知识库搜；
         * 给出链接才是真正可用的出处。
         */
        public Long articleId() {
            try {
                return Long.parseLong(id);
            } catch (Exception e) {
                return null;
            }
        }

        public String getTitle() {
            return meta.getStr("title");
        }

        public String getCategoryName() {
            return meta.getStr("categoryName");
        }

        public String getExcerpt() {
            return meta.getStr("excerpt");
        }
    }
}
