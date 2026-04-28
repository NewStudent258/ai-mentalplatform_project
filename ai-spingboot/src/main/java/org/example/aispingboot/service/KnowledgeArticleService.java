package org.example.aispingboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.example.aispingboot.AiService.mq.IndexMessageProducer;
import org.example.aispingboot.DTO.command.KnowledgeArticleCommandDTO;
import org.example.aispingboot.enumClass.ArticleStatus;
import org.example.aispingboot.DTO.command.KnowledgeArticleQueryDTO;
import org.example.aispingboot.common.PageResult;
import org.example.aispingboot.entity.KnowledgeArticle;
import org.example.aispingboot.entity.KnowledgeCategory;
import org.example.aispingboot.exception.BusinessException;
import org.example.aispingboot.mapper.KnowledgeArticleMapper;
import org.example.aispingboot.mapper.KnowledgeCategoryMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class KnowledgeArticleService {
    @Resource
    private KnowledgeArticleMapper knowledgeArticleMapper;

    @Resource
    private KnowledgeCategoryMapper knowledgeCategoryMapper;

    /**
     * 索引变更走消息队列异步处理。
     * <p>
     * 向量化要调用 Embedding 接口，耗时且可能失败；若同步执行，
     * 管理员「点发布」需要一直等到向量化结束，且失败会直接影响发布结果。
     * 改为投递消息后，发布立即返回，向量化在后台完成并支持重试。
     */
    @Resource
    private IndexMessageProducer indexMessageProducer;

    /**
     * 分页查询文章
     * 列表查询高频且允许短暂延迟，命中 Redis 缓存（10分钟TTL）
     */
    @Cacheable(value = "articlePage", key = "#query.currentPage + ':' + #query.size + ':' + #query.title + ':' + #query.categoryId + ':' + #query.status + ':' + #query.sortField + ':' + #query.sortDirection")
    public PageResult<KnowledgeArticle> page(KnowledgeArticleQueryDTO query) {
        int currentPage = query.getCurrentPage() == null ? 1 : query.getCurrentPage();
        int size = query.getSize() == null ? 10 : Math.min(query.getSize(), 50);
        Page<KnowledgeArticle> page = new Page<>(currentPage, size);

        LambdaQueryWrapper<KnowledgeArticle> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getTitle())) {
            wrapper.like(KnowledgeArticle::getTitle, query.getTitle());
        }
        if (query.getCategoryId() != null) {
            wrapper.eq(KnowledgeArticle::getCategoryId, query.getCategoryId());
        }
        if (query.getStatus() != null) {
            wrapper.eq(KnowledgeArticle::getStatus, query.getStatus());
        }
        // 排序（白名单字段）
        boolean asc = "asc".equalsIgnoreCase(query.getSortDirection());
        if ("readCount".equals(query.getSortField())) {
            wrapper.orderBy(true, asc, KnowledgeArticle::getReadCount);
        } else if ("publishedAt".equals(query.getSortField())) {
            wrapper.orderBy(true, asc, KnowledgeArticle::getPublishedAt);
        } else {
            wrapper.orderBy(true, asc, KnowledgeArticle::getUpdatedAt);
        }
        wrapper.orderByDesc(KnowledgeArticle::getId);

        knowledgeArticleMapper.selectPage(page, wrapper);
        fillCategoryName(page.getRecords());
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getSize(), page.getCurrent());
    }

    /**
     * 按关键词检索可被 AI 引用的文章，供 AI 工具的兜底提示使用。
     * <p>
     * 与后台的 {@link #page} 有三个关键区别：
     * 1. 强制过滤 status=1（已发布）——草稿、待审核、已驳回、已下线内容绝不能经 AI 泄露；
     * 2. 强制过滤 citable=1——仅展示不可引用的投稿（如个人经验分享）不得进入 AI 视野；
     * 3. 同时匹配标题、摘要、标签、正文，命中面更广。
     *
     * @param keyword 检索关键词，为空时返回阅读量最高的文章
     * @param limit   返回条数上限（内部会钳制到安全范围）
     */
    public List<KnowledgeArticle> searchPublished(String keyword, int limit) {
        // limit 会被拼进 SQL 的 limit 子句，必须先钳制到安全区间，避免注入
        int safeLimit = Math.max(1, Math.min(limit, 10));

        LambdaQueryWrapper<KnowledgeArticle> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeArticle::getStatus, ArticleStatus.PUBLISHED.getCode())
                .eq(KnowledgeArticle::getCitable, 1);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            // 用 and(...) 包一层括号，避免 or 条件与上面的 status 过滤发生优先级错误，
            // 否则会退化成「已发布的 OR 命中关键词的」，把未发布文章一并查出来
            wrapper.and(w -> w.like(KnowledgeArticle::getTitle, kw)
                    .or().like(KnowledgeArticle::getSummary, kw)
                    .or().like(KnowledgeArticle::getTags, kw)
                    .or().like(KnowledgeArticle::getContent, kw));
        }
        wrapper.orderByDesc(KnowledgeArticle::getReadCount)
                .orderByDesc(KnowledgeArticle::getId)
                .last("limit " + safeLimit);

        List<KnowledgeArticle> articles = knowledgeArticleMapper.selectList(wrapper);
        fillCategoryName(articles);
        return articles;
    }

    /**
     * 查询文章详情（阅读量+1）
     */
    public KnowledgeArticle getDetail(Long id) {
        KnowledgeArticle article = knowledgeArticleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        // 阅读量+1
        LambdaUpdateWrapper<KnowledgeArticle> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(KnowledgeArticle::getId, id)
                .setSql("read_count = read_count + 1");
        knowledgeArticleMapper.update(null, updateWrapper);
        article = knowledgeArticleMapper.selectById(id);
        fillCategoryName(List.of(article));
        return article;
    }

    /**
     * 创建文章（默认草稿状态）
     */
    @CacheEvict(value = "articlePage", allEntries = true)
    public KnowledgeArticle create(KnowledgeArticleCommandDTO commandDTO, String authorName) {
        // 校验分类是否存在
        if (knowledgeCategoryMapper.selectById(commandDTO.getCategoryId()) == null) {
            throw new BusinessException("文章分类不存在");
        }
        KnowledgeArticle article = new KnowledgeArticle();
        article.setTitle(commandDTO.getTitle());
        article.setContent(commandDTO.getContent());
        article.setSummary(commandDTO.getSummary());
        article.setCoverImage(commandDTO.getCoverImage());
        article.setCategoryId(commandDTO.getCategoryId());
        article.setAuthorName(authorName);
        article.setTags(commandDTO.getTags());
        article.setReadCount(0);
        article.setStatus(0);
        knowledgeArticleMapper.insert(article);
        return article;
    }

    /**
     * 更新文章
     */
    @CacheEvict(value = "articlePage", allEntries = true)
    public KnowledgeArticle update(Long id, KnowledgeArticleCommandDTO commandDTO) {
        KnowledgeArticle article = knowledgeArticleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        if (knowledgeCategoryMapper.selectById(commandDTO.getCategoryId()) == null) {
            throw new BusinessException("文章分类不存在");
        }
        article.setTitle(commandDTO.getTitle());
        article.setContent(commandDTO.getContent());
        article.setSummary(commandDTO.getSummary());
        article.setCoverImage(commandDTO.getCoverImage());
        article.setCategoryId(commandDTO.getCategoryId());
        article.setTags(commandDTO.getTags());
        knowledgeArticleMapper.updateById(article);
        // 正文/标题可能已变，重建索引，避免 AI 检索到过期内容
        indexMessageProducer.sendIndex(id);
        return article;
    }

    /**
     * 变更文章状态（发布时记录首次发布时间）
     */
    @CacheEvict(value = "articlePage", allEntries = true)
    public void updateStatus(Long id, Integer status) {
        KnowledgeArticle article = knowledgeArticleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        if (status == 1 && article.getPublishedAt() == null) {
            article.setPublishedAt(LocalDateTime.now());
        }
        article.setStatus(status);
        knowledgeArticleMapper.updateById(article);
        // 状态变更直接决定该文章能否被 AI 引用：发布则建索引，下线则移出索引。
        // 必须先落库再投递消息——消费者会回查数据库判断该建还是该删，
        // 顺序反了会让消费者读到旧状态，索引与数据不一致。
        indexMessageProducer.sendIndex(id);
    }

    /**
     * 删除文章
     */
    @CacheEvict(value = "articlePage", allEntries = true)
    public void delete(Long id) {
        if (knowledgeArticleMapper.selectById(id) == null) {
            throw new BusinessException("文章不存在");
        }
        knowledgeArticleMapper.deleteById(id);
        // 文章已删除，索引必须一并清掉，否则 AI 会引用一篇已经不存在的文章
        indexMessageProducer.sendRemove(id);
    }

    // ==================== 用户投稿 ====================

    /** 作者类型：系统/管理员创建 */
    public static final int AUTHOR_TYPE_SYSTEM = 1;
    /** 作者类型：用户投稿 */
    public static final int AUTHOR_TYPE_USER = 2;

    /**
     * 用户投稿：创建一篇处于「待审核」状态的文章。
     * <p>
     * 两个关键约束：
     * 1. 状态直接置为待审核，用户无法自行发布；
     * 2. citable 置 0 —— 个人经验分享默认可展示、不可被 AI 引用，
     *    是否提升为可引用由管理员在审核时决定。
     */
    public KnowledgeArticle submitByUser(KnowledgeArticleCommandDTO commandDTO, Long userId, String authorName) {
        if (knowledgeCategoryMapper.selectById(commandDTO.getCategoryId()) == null) {
            throw new BusinessException("文章分类不存在");
        }
        KnowledgeArticle article = new KnowledgeArticle();
        article.setTitle(commandDTO.getTitle());
        article.setContent(commandDTO.getContent());
        article.setSummary(commandDTO.getSummary());
        article.setCoverImage(commandDTO.getCoverImage());
        article.setCategoryId(commandDTO.getCategoryId());
        article.setAuthorName(authorName);
        article.setTags(commandDTO.getTags());
        article.setReadCount(0);
        article.setStatus(ArticleStatus.PENDING_REVIEW.getCode());
        article.setAuthorId(userId);
        article.setAuthorType(AUTHOR_TYPE_USER);
        // 默认不可被 AI 引用，待审核时由管理员显式授权
        article.setCitable(0);
        knowledgeArticleMapper.insert(article);
        return article;
    }

    /**
     * 查询某用户自己的投稿（含各种状态，便于用户看到驳回原因）
     */
    public List<KnowledgeArticle> listMySubmissions(Long userId) {
        LambdaQueryWrapper<KnowledgeArticle> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeArticle::getAuthorId, userId)
                .eq(KnowledgeArticle::getAuthorType, AUTHOR_TYPE_USER)
                .orderByDesc(KnowledgeArticle::getCreatedAt)
                .orderByDesc(KnowledgeArticle::getId);
        List<KnowledgeArticle> articles = knowledgeArticleMapper.selectList(wrapper);
        fillCategoryName(articles);
        return articles;
    }

    /**
     * 用户删除自己的投稿。
     * <p>
     * 只允许删除自己的、且尚未发布的投稿：已发布的内容若允许作者自行删除，
     * 会造成读者侧内容突然消失，应由管理员下线处理。
     */
    public void deleteMySubmission(Long id, Long userId) {
        KnowledgeArticle article = knowledgeArticleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException("投稿不存在");
        }
        if (!userId.equals(article.getAuthorId()) || !Integer.valueOf(AUTHOR_TYPE_USER).equals(article.getAuthorType())) {
            throw new BusinessException("只能删除自己的投稿");
        }
        if (ArticleStatus.PUBLISHED.getCode().equals(article.getStatus())) {
            throw new BusinessException("投稿已发布，如需下架请联系管理员");
        }
        knowledgeArticleMapper.deleteById(id);
        indexMessageProducer.sendRemove(id);
    }

    // ==================== 管理员审核 ====================

    /**
     * 待审核投稿列表（按提交时间正序，先提交先审）
     */
    public List<KnowledgeArticle> listPendingReview() {
        LambdaQueryWrapper<KnowledgeArticle> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeArticle::getStatus, ArticleStatus.PENDING_REVIEW.getCode())
                .orderByAsc(KnowledgeArticle::getCreatedAt)
                .orderByAsc(KnowledgeArticle::getId);
        List<KnowledgeArticle> articles = knowledgeArticleMapper.selectList(wrapper);
        fillCategoryName(articles);
        return articles;
    }

    /**
     * 审核投稿。
     *
     * @param approved     是否通过
     * @param citable      通过时是否允许被 AI 引用
     * @param rejectReason 驳回原因（驳回时必填）
     * @param reviewerId   审核人ID
     */
    @CacheEvict(value = "articlePage", allEntries = true)
    public KnowledgeArticle review(Long id, boolean approved, boolean citable,
                                   String rejectReason, Long reviewerId) {
        KnowledgeArticle article = knowledgeArticleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException("投稿不存在");
        }
        if (!ArticleStatus.PENDING_REVIEW.getCode().equals(article.getStatus())) {
            throw new BusinessException("该投稿不处于待审核状态，无法重复审核");
        }

        if (approved) {
            article.setStatus(ArticleStatus.PUBLISHED.getCode());
            if (article.getPublishedAt() == null) {
                article.setPublishedAt(LocalDateTime.now());
            }
            article.setCitable(citable ? 1 : 0);
            article.setRejectReason(null);
        } else {
            if (!StringUtils.hasText(rejectReason)) {
                throw new BusinessException("驳回时必须填写原因");
            }
            article.setStatus(ArticleStatus.REJECTED.getCode());
            article.setRejectReason(rejectReason.trim());
        }
        article.setReviewedBy(reviewerId);
        article.setReviewedAt(LocalDateTime.now());
        knowledgeArticleMapper.updateById(article);

        // 按审核结果同步向量索引：通过且授权引用才入库，其余一律移出。
        // 具体该建还是该删由消费者回查数据库状态决定，这里只负责通知。
        indexMessageProducer.sendIndex(id);
        return article;
    }

    /**
     * 批量填充分类名称和标签数组
     */
    private void fillCategoryName(List<KnowledgeArticle> articles) {
        if (articles == null || articles.isEmpty()) {
            return;
        }
        Set<Long> categoryIds = articles.stream()
                .map(KnowledgeArticle::getCategoryId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, String> categoryMap = Map.of();
        if (!categoryIds.isEmpty()) {
            categoryMap = knowledgeCategoryMapper.selectBatchIds(categoryIds).stream()
                    .collect(Collectors.toMap(KnowledgeCategory::getId, KnowledgeCategory::getCategoryName));
        }
        Map<Long, String> finalCategoryMap = categoryMap;
        articles.forEach(article -> {
            article.setCategoryName(finalCategoryMap.get(article.getCategoryId()));
            article.setTagArray(splitTags(article.getTags()));
        });
    }

    /**
     * 逗号分隔的标签字符串转数组
     */
    private List<String> splitTags(String tags) {
        if (!StringUtils.hasText(tags)) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
