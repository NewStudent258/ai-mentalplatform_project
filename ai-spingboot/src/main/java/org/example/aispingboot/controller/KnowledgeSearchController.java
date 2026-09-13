package org.example.aispingboot.controller;

import org.example.aispingboot.AiService.HybridRetriever;
import org.example.aispingboot.AiService.KnowledgeVectorStore;
import org.example.aispingboot.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库检索接口。
 * <p>
 * <b>它的两个用途</b>：
 * <ol>
 *   <li><b>评测</b>：检索质量评测脚本通过本接口跑数据，因此测的是<b>真实实现</b>，
 *       而不是另写一套「看起来一样」的逻辑——后者测不出真实问题；</li>
 *   <li><b>前端搜索</b>：知识库页面的搜索框可以直接用。</li>
 * </ol>
 * <p>
 * 需要登录才能访问：检索会调用 Embedding 接口（按量计费），
 * 不鉴权会被刷额度。
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeSearchController {

    /** 返回条数上限，防止一次拉取过多 */
    private static final int MAX_TOP_K = 20;

    @Autowired
    private HybridRetriever hybridRetriever;

    /**
     * 检索知识库。
     *
     * @param query 查询内容
     * @param topK  返回条数，默认 5
     * @param mode  检索模式 VECTOR / KEYWORD / HYBRID，默认 HYBRID。
     *              保留该参数是为了让评测脚本能对同一份评测集跑不同方案做对比。
     * @return 按相关度降序的文章列表（含 id / 标题 / 分数）
     */
    @GetMapping("/search")
    public Result<List<Map<String, Object>>> search(
            @RequestParam String query,
            @RequestParam(required = false, defaultValue = "5") Integer topK,
            @RequestParam(required = false) String mode) {
        int limit = Math.max(1, Math.min(topK, MAX_TOP_K));

        HybridRetriever.Mode searchMode;
        try {
            searchMode = mode == null ? HybridRetriever.Mode.HYBRID
                    : HybridRetriever.Mode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 非法模式回退到默认值，而不是报错——评测时传错参数不应导致整轮中断
            searchMode = HybridRetriever.Mode.HYBRID;
        }

        List<KnowledgeVectorStore.ScoredArticle> hits =
                hybridRetriever.search(query, limit, searchMode);

        List<Map<String, Object>> result = hits.stream().map(hit -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", hit.articleId());
            item.put("title", hit.getTitle());
            // 保留 4 位小数：评测需要对比不同方案的排序细节
            item.put("score", Math.round(hit.score * 10000) / 10000.0);
            return item;
        }).toList();

        return Result.ok(result);
    }
}
