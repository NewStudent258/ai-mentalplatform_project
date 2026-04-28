package org.example.aispingboot.DTO.command;

import lombok.Data;

/**
 * 知识文章分页查询参数
 */
@Data
public class KnowledgeArticleQueryDTO {
    // 页码
    private Integer currentPage = 1;

    // 每页条数
    private Integer size = 10;

    // 排序字段: publishedAt/readCount/updatedAt
    private String sortField;

    // 排序方向: asc/desc
    private String sortDirection;

    // 文章标题(模糊查询)
    private String title;

    // 分类ID
    private Long categoryId;

    // 状态 0:草稿 1:已发布 2:已下线
    private Integer status;
}
