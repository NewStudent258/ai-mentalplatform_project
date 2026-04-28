package org.example.aispingboot.AiService.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 文章索引变更消息。
 * <p>
 * 消息里<b>只携带文章ID和操作类型，不携带文章内容</b>，这是刻意的设计：
 * <ul>
 *   <li>消息体小，且不必随文章内容增长</li>
 *   <li>消费者总是读取数据库中的<b>最新</b>状态，因此同一篇文章的
 *       多条消息无论以什么顺序到达，最终都会收敛到正确结果——天然幂等且顺序无关</li>
 *   <li>避免「消息里的旧内容覆盖了数据库新内容」这类脏写</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IndexMessage implements Serializable {

    /** 操作类型：建立/更新索引 */
    public static final String OP_INDEX = "INDEX";
    /** 操作类型：移除索引 */
    public static final String OP_REMOVE = "REMOVE";

    /** 文章ID */
    private Long articleId;

    /** 操作类型，见本类常量 */
    private String operation;

    public static IndexMessage index(Long articleId) {
        return new IndexMessage(articleId, OP_INDEX);
    }

    public static IndexMessage remove(Long articleId) {
        return new IndexMessage(articleId, OP_REMOVE);
    }
}
