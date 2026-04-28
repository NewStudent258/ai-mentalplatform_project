package org.example.aispingboot.DTO.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识文章创建/更新参数
 */
@Data
public class KnowledgeArticleCommandDTO {
    // 文章标题
    @NotBlank(message = "文章标题不能为空")
    @Size(max = 200, message = "文章标题最多200个字符")
    private String title;

    // 富文本内容
    @NotBlank(message = "文章内容不能为空")
    @Size(max = 50000, message = "文章内容最多50000个字符")
    private String content;

    // 文章摘要
    @Size(max = 1000, message = "文章摘要最多1000个字符")
    private String summary;

    // 封面图片路径
    @Size(max = 255, message = "封面图片路径最多255个字符")
    private String coverImage;

    // 分类ID
    @NotNull(message = "文章分类不能为空")
    private Long categoryId;

    // 标签(逗号分隔)
    @Size(max = 500, message = "标签最多500个字符")
    private String tags;

    // 前端上传封面时生成的业务ID，后端忽略
    private String id;
}
