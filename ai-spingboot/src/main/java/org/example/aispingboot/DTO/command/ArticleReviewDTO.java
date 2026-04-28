package org.example.aispingboot.DTO.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 投稿审核参数
 */
@Data
public class ArticleReviewDTO {

    // 是否通过
    @NotNull(message = "请指定审核结果")
    private Boolean approved;

    /**
     * 通过时，是否允许该文章被 AI 引用。
     * <p>
     * 与「是否发布」分开决策：能够展示给读者 ≠ 可以被 AI 当作专业依据转述。
     * 个人经验分享类内容适合展示，但不适合作为权威建议引用。
     * 未传时按不允许处理（保守优先）。
     */
    private Boolean citable;

    // 驳回原因，驳回时必填（由服务层校验）
    @Size(max = 500, message = "驳回原因最多500个字符")
    private String rejectReason;
}
