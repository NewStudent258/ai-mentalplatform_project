package org.example.aispingboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识文章实体类
 */
@Data
@TableName("knowledge_article")
public class KnowledgeArticle {
    // 文章ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 文章标题
    private String title;

    // 富文本内容
    private String content;

    // 文章摘要
    private String summary;

    // 封面图片路径
    @TableField("cover_image")
    private String coverImage;

    // 分类ID
    @TableField("category_id")
    private Long categoryId;

    // 作者名称
    @TableField("author_name")
    private String authorName;

    // 标签(逗号分隔)
    private String tags;

    // 阅读量
    @TableField("read_count")
    private Integer readCount;

    // 状态 0:草稿 1:已发布 2:已下线 3:待审核 4:已驳回（见 ArticleStatus 枚举）
    private Integer status;

    // 投稿用户ID；NULL 表示由管理员/系统创建
    @TableField("author_id")
    private Long authorId;

    // 作者类型 1:系统/管理员 2:用户投稿
    @TableField("author_type")
    private Integer authorType;

    /**
     * 是否允许被 AI 引用。
     * <p>
     * 刻意与 status 解耦：能够「展示」不等于可以被 AI 当作专业依据引用。
     * 用户投稿的个人经验可以展示给读者，但不应被 AI 作为权威建议转述给他人，
     * 因此在审核时单独决定，只有 citable=1 的文章才会进入向量索引。
     */
    private Integer citable;

    // 审核人ID
    @TableField("reviewed_by")
    private Long reviewedBy;

    // 审核时间
    @TableField("reviewed_at")
    private LocalDateTime reviewedAt;

    // 驳回原因（仅 status=4 时有值）
    @TableField("reject_reason")
    private String rejectReason;

    // 发布时间
    @TableField("published_at")
    private LocalDateTime publishedAt;

    // 创建时间
    @TableField("created_at")
    private LocalDateTime createdAt;

    // 更新时间
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    // 分类名称（非表字段，查询时填充）
    @TableField(exist = false)
    private String categoryName;

    // 标签数组（非表字段，查询时填充）
    @TableField(exist = false)
    private List<String> tagArray;
}
