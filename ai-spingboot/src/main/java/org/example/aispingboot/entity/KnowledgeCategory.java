package org.example.aispingboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识文章分类实体类
 */
@Data
@TableName("knowledge_category")
public class KnowledgeCategory {
    // 分类ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 分类名称
    @TableField("category_name")
    private String categoryName;

    // 父分类ID，0为顶级
    @TableField("parent_id")
    private Long parentId;

    // 排序号
    @TableField("sort_order")
    private Integer sortOrder;

    // 状态 0:禁用 1:启用
    private Integer status;

    // 创建时间
    @TableField("created_at")
    private LocalDateTime createdAt;

    // 更新时间
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
