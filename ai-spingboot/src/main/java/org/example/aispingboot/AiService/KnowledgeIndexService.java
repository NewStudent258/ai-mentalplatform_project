package org.example.aispingboot.AiService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.aispingboot.entity.KnowledgeArticle;
import org.example.aispingboot.mapper.KnowledgeArticleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库向量索引的构建与同步。
 * <p>
 * 向量库是文章数据的「派生副本」，必须与文章表保持一致，否则会出现
 * 「文章已下线但 AI 还在引用」这类严重的内容治理问题。因此这里做两件事：
 * <ol>
 *   <li>启动时全量重建：兜住服务停机期间的变更、Redis 清空等异常情况；</li>
 *   <li>文章增删改时增量同步：保证内容改动后检索结果立即跟随。</li>
 * </ol>
 */
@Service
public class KnowledgeIndexService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexService.class);

    /** 已发布状态：只有已发布的文章才允许进入 AI 知识库 */
    private static final int STATUS_PUBLISHED = 1;
    /** 允许被 AI 引用 */
    private static final int CITABLE_YES = 1;

    private final KnowledgeArticleMapper knowledgeArticleMapper;
    private final KnowledgeVectorStore knowledgeVectorStore;

    public KnowledgeIndexService(KnowledgeArticleMapper knowledgeArticleMapper,
                                 KnowledgeVectorStore knowledgeVectorStore) {
        this.knowledgeArticleMapper = knowledgeArticleMapper;
        this.knowledgeVectorStore = knowledgeVectorStore;
    }

    /**
     * 启动后全量重建索引。
     * <p>
     * 使用 ApplicationReadyEvent 而非构造时执行：此时应用已完全就绪，
     * 不会拖慢启动，且即使索引构建失败也不影响服务对外提供能力。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void reindexAllOnStartup() {
        try {
            LambdaQueryWrapper<KnowledgeArticle> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(KnowledgeArticle::getStatus, STATUS_PUBLISHED)
                    .eq(KnowledgeArticle::getCitable, CITABLE_YES);
            List<KnowledgeArticle> articles = knowledgeArticleMapper.selectList(wrapper);

            if (articles.isEmpty()) {
                log.info("知识库暂无已发布文章，跳过向量索引构建");
                return;
            }

            // 对账：只补建 Redis 中缺失的向量，而不是每篇都重新调用 Embedding。
            // 原因有两方面——一是 Embedding 是付费接口，无谓的重复调用是浪费；
            // 二是短时间密集调用容易触发限流（Connection reset），
            // 反而让本该补建的文章再次失败。
            int missing = 0, success = 0;
            for (KnowledgeArticle article : articles) {
                if (knowledgeVectorStore.isIndexed(article.getId())) {
                    continue;
                }
                missing++;
                if (indexOne(article)) {
                    success++;
                }
                // 逐篇之间稍作间隔，避免触发向量化服务的限流
                sleepQuietly(150);
            }

            if (missing == 0) {
                log.info("知识库向量索引校验完成：{} 篇均已建立索引", articles.size());
            } else {
                // 有缺失说明此前存在 Embedding 调用失败。失败的文章不会被 AI 检索到，
                // 必须显式告警，否则这类遗漏会一直潜伏到用户发现「AI 答不出知识库里明明有的内容」
                log.warn("知识库向量索引补建：发现 {} 篇缺失，成功补建 {} 篇（共 {} 篇已发布）",
                        missing, success, articles.size());
            }
        } catch (Exception e) {
            // 索引构建失败不应阻止应用启动：语义检索会降级为空结果，
            // 对话仍可正常进行（工具返回「未找到」，模型转用自身知识回答）
            log.warn("知识库向量索引构建失败，语义检索将不可用：{}", e.getMessage());
        }
    }

    /**
     * 同步单篇文章的索引状态。
     * <p>
     * 只有「已发布 <b>且</b> 允许被 AI 引用」的文章才建索引，其余情况一律移除。
     * 用「移除」而不是「跳过」，是为了保证文章下线、或取消引用授权后，AI 立即无法再引用它。
     * <p>
     * 注意这里是 <b>与</b> 关系而非或：一篇已发布但 citable=0 的投稿，
     * 对读者可见、但绝不能进入 AI 知识库。
     */
    public boolean syncArticle(Long articleId) {
        if (articleId == null) {
            return false;
        }
        try {
            KnowledgeArticle article = knowledgeArticleMapper.selectById(articleId);
            if (!isIndexable(article)) {
                // 文章不存在、未发布或不允许引用：移除索引即为正确处理结果
                knowledgeVectorStore.remove(articleId);
                return true;
            }
            // 向量化失败必须返回 false 而不能吞掉：
            // 调用方（MQ 消费者）据此决定重试，否则消息会被误判为处理成功而 ACK，
            // 导致这篇文章永远检索不到——正是引入 MQ 要解决的问题
            return indexOne(article);
        } catch (Exception e) {
            log.warn("同步文章 {} 向量索引失败：{}", articleId, e.getMessage());
            return false;
        }
    }

    /** 判断文章是否具备进入 AI 知识库的资格 */
    private boolean isIndexable(KnowledgeArticle article) {
        return article != null
                && Integer.valueOf(STATUS_PUBLISHED).equals(article.getStatus())
                && Integer.valueOf(CITABLE_YES).equals(article.getCitable());
    }

    /**
     * 移除文章索引（文章被删除时调用）
     */
    public boolean removeArticle(Long articleId) {
        try {
            knowledgeVectorStore.remove(articleId);
            return true;
        } catch (Exception e) {
            log.warn("移除文章 {} 向量索引失败：{}", articleId, e.getMessage());
            return false;
        }
    }

    private boolean indexOne(KnowledgeArticle article) {
        try {
            return knowledgeVectorStore.index(article);
        } catch (Exception e) {
            log.warn("文章 {} 向量化失败：{}", article.getId(), e.getMessage());
            return false;
        }
    }

    /** 间隔一小段时间，避免密集调用触发向量化服务限流 */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // 保留中断标记，交由上层决定是否终止
            Thread.currentThread().interrupt();
        }
    }
}
